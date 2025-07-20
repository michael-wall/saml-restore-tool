package com.mw.saml.restore.tool.service.impl;

import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.petra.string.StringBundler;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.FileUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.saml.persistence.model.SamlSpIdpConnection;
import com.liferay.saml.persistence.service.SamlSpIdpConnectionLocalService;
import com.liferay.saml.runtime.configuration.SamlProviderConfigurationHelper;
import com.liferay.saml.runtime.credential.KeyStoreManager;
import com.liferay.saml.runtime.metadata.LocalEntityManager;
import com.mw.saml.restore.tool.service.constants.SamlRestoreToolConstants;
import com.mw.saml.restore.tool.service.model.CommonEnvironmentVariableConfig;
import com.mw.saml.restore.tool.service.model.IdPConfig;
import com.mw.saml.restore.tool.service.model.SPConfig;
import com.mw.saml.restore.tool.service.model.VirtualInstanceSecretConfig;
import com.mw.saml.restore.tool.service.util.SamlRestoreToolUtil;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
	immediate = true,
	property = {"osgi.command.scope=samlRestoreTool", "osgi.command.function=restoreSamlConfig"}, 
	configurationPid = "com.liferay.saml.runtime.configuration.SamlKeyStoreManagerConfiguration",
	service = SamlRestoreToolServiceImpl.class)
public class SamlRestoreToolServiceImpl {
	
	@Activate
	protected void activate(Map<String, Object> properties)  throws Exception {
		_log.info("activated");
	}	
	
	public String restoreSamlConfig() {
		_log.info("Started running restoreSamlConfig.");
		_log.info("Environment uses " + _keyStoreManager.getClass().getCanonicalName());

		if (_keyStoreManager != null && !_keyStoreManager.getClass().getCanonicalName().equalsIgnoreCase(SamlRestoreToolConstants.DOCUMENT_LIBRARY_KEYSTORE_MANAGER)) {
			String output = "SAML KeyStoreManager Implementation Configuration > Keystore Manager Target not set to Document Library Keystore Manager. Not proceeding.";
			_log.info(output);
	
			return output;
		}
					
		CommonEnvironmentVariableConfig commonConfig = _getCommonConfig();
	
		if (!commonConfig.isEnabled()) {
			String output = SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.REGULAR.SAML_RESTORE_TOOL_ENABLED + " not set to true. Not proceeding.";
			_log.info(output);
			
			return output;
		}
		
		if (!commonConfig.isValid()) {
			String output = "Environment Variables not configured as expected. Not proceeding.";
			_log.info(output);
			
			return output;
		}
		
		Properties portalProperties = _portal.getPortalProperties();

		String liferayHome = portalProperties.getProperty(PropsKeys.LIFERAY_HOME);
		String relativePathToFolder = commonConfig.getConfigPath();

		if (Validator.isNull(relativePathToFolder) || Validator.isNull(liferayHome)) {
			String output = "Path configuration not as expected. Not proceeding.";
			_log.info(output);
			
			return output;
		}

		List<String> virtualInstancesSAMLRestored = new ArrayList<>();
		List<String> virtualInstancesSAMLRestoreUnsuccessful = new ArrayList<>();
		int virtualInstanceErrorCount = 0;
		
		String configPath = liferayHome + relativePathToFolder;
		File configRootFolder = new File(configPath);
		
		if (!configRootFolder.exists() || !configRootFolder.isDirectory()) {
			String output = "Folder " + configPath + " not found. Not proceeding.";
			_log.info(output);
			
			return output;
		}
		
		File[] folders = configRootFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory();
			}
		});
		
		if (folders.length == 0) {
			String output = "No folders found within folder " + configPath + ". Not proceeding.";
			_log.info(output);
			
			return output;
		}
		for (File instanceFolder : configRootFolder.listFiles()) {
			if (!instanceFolder.isDirectory()) { // Skip if not folder
				continue;
			}
		
			String virtualInstanceFolderName = instanceFolder.getName();
			
			_log.info(virtualInstanceFolderName + ": Started processing SAML restore configuration.");
			
			File[] folderFiles = instanceFolder.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isFile();
				}
			});
			
			if (folderFiles == null || folderFiles.length < 3) {
				_log.info(virtualInstanceFolderName + ": Folder doesn't contain expected files.");
				
				virtualInstanceErrorCount ++;
				virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
				
				continue; // Skip to next folder		
			}
			
			if (!containsFile(folderFiles, SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE)) {
				_log.info(virtualInstanceFolderName + ": Configuration file " + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE + " is missing.");
				
				virtualInstanceErrorCount ++;
				virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
				
				continue; // Skip to next folder		
			}
			
			List<IdPConfig> idPConfigs = new ArrayList<IdPConfig>();
			long invalidIdPCount = 0;
			
			SPConfig spConfig = null;
			Company company = null;
			
			try {
				Properties samlAdminConfigurationProperties = SamlRestoreToolUtil.loadPropertiesFile(instanceFolder.getPath() + StringPool.FORWARD_SLASH + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE);
				
				if (Validator.isNull(samlAdminConfigurationProperties)) {
					_log.info(virtualInstanceFolderName + ": Unable to load properties from " + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE + ".");
					
					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
					
					continue; // Skip to next folder
				}
					
				//Basic Validation of properties before progressing...
				boolean isValidBasic = SamlRestoreToolUtil.isValidBasic(_idpPopertyPrefix(1), samlAdminConfigurationProperties);
				
				if (!isValidBasic) {
					_log.info(virtualInstanceFolderName + ": Basic validation of " + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE + " failed.");
					
					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
					
					continue; // Skip to next folder
				}
				
				String restorableKeyStoreFileName = samlAdminConfigurationProperties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.KEY_STORE_FILE);
				
				if (!containsFile(folderFiles, restorableKeyStoreFileName)) {
					_log.info(virtualInstanceFolderName + ": Keystore file " + restorableKeyStoreFileName + " is missing.");
					
					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
					
					continue; // Skip to next folder					
				}
				
				String virtualHost = samlAdminConfigurationProperties.getProperty(SamlRestoreToolConstants.PROPERTIES.MAPPING.COMPANY_VIRTUAL_HOST);
				String secretParamValue = samlAdminConfigurationProperties.getProperty(SamlRestoreToolConstants.PROPERTIES.MAPPING.SECRET_PARAM);
				
				if (!Validator.isNull(virtualHost)) {
					company = _companyLocalService.fetchCompanyByVirtualHost(virtualHost);
				}
					
				if (company == null) {
					_log.info(virtualInstanceFolderName + ": Company not found by fetchCompanyByVirtualHost for company.virtual.host " + virtualHost + ".");
					
					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}
					
				VirtualInstanceSecretConfig virtualInstanceConfig = _getVirtualInstanceConfig(secretParamValue);
					
				//Validate the secrets exist etc.
				if (!virtualInstanceConfig.isValid()) {
					_log.info(virtualInstanceFolderName + ": Mandatory secrets not configured as expected.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
					
					continue; // Skip to next folder
				}
				
				CompanyThreadLocal.setCompanyId(company.getCompanyId());

				if (_samlProviderConfigurationHelper.getSamlProviderConfiguration() == null || _samlProviderConfigurationHelper.getSamlProviderConfiguration().companyId() != company.getCompanyId()) {
					_log.info(virtualInstanceFolderName + ": SAML SP Configuration missing.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}
				
				if (!_samlProviderConfigurationHelper.isRoleSp()) {
					_log.info(virtualInstanceFolderName + ": Unexpected SAML Role.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}
				
				spConfig = SamlRestoreToolUtil.parseSPConfig(samlAdminConfigurationProperties);

				if (spConfig.hasEncryptionCert() && Validator.isNull(virtualInstanceConfig.getEncryptionCertificatePassword())) {
					String encryptionCertificatePasswordEnvVar = SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_PARAM.replaceAll("\\{0\\}", secretParamValue);
						
					_log.info(virtualInstanceFolderName + ": Secret " + encryptionCertificatePasswordEnvVar + " is missing.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}				

				for (int i = 1; i <= 10; i++) { // Assume no more than 10...
					String dynamicPrefix = _idpPopertyPrefix(i);
				
					//Basic checks before loading fully and more detailed validation...
					String idPName = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.CONNECTION_NAME);
					String idPEntityId = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.SAML_IDP_ENTITY_ID);
					String idPMetadataFile = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.IDP_METADATA_FILE);

					if (Validator.isNotNull(idPName) || Validator.isNotNull(idPEntityId) || Validator.isNotNull(idPMetadataFile)) {
						boolean metaDataFileExists = containsFile(folderFiles, idPMetadataFile);
						
						if (!metaDataFileExists) {
							_log.info(virtualInstanceFolderName + ": IdP configuration " + _idpPopertyPrefix(i) + " metadata file " + idPMetadataFile + " not found.");
							
							invalidIdPCount ++;
							
							break;
						} else {
							IdPConfig idpConfig = SamlRestoreToolUtil.parseIdPConfig(dynamicPrefix, company.getCompanyId(), samlAdminConfigurationProperties);
							
							//Basic Validation of this set of IdP properties
							if (idpConfig.isValidBasic()) {
								idPConfigs.add(idpConfig);
							} else {
								_log.info(virtualInstanceFolderName + ": IdP configuration " + _idpPopertyPrefix(i) + " is invalid / incomplete.");
								
								invalidIdPCount ++;
								
								break;
							}							
						}
					} else {
						break;
					}
				}
					
				_log.info(virtualInstanceFolderName + ": Valid IdP count is " + idPConfigs.size() + ".");
				
				if (idPConfigs.isEmpty()) {
					_log.info(virtualInstanceFolderName + ": No Valid IdP configurations in the properties file.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
					
					continue; // Skip to next folder
				} else if (invalidIdPCount > 0) {
					_log.info(virtualInstanceFolderName + ": One of more invalid or incomplete IdP configurations in the properties file.");

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}

				KeyStore virtualInstanceKeyStore = _replaceVirtualInstanceKeyStore(spConfig.hasEncryptionCert(), spConfig.getSamlSpEntityId(), instanceFolder, restorableKeyStoreFileName, virtualInstanceConfig, virtualInstanceFolderName);

				if (Validator.isNull(virtualInstanceKeyStore)) {
					_log.error(virtualInstanceFolderName + ": An error occurred updating the KeyStore.");
					
					_configureSamlSpProperties(false, spConfig, null, virtualInstanceFolderName, true, false);

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue; // Skip to next folder
				}

				// Disabled temporarily while the Idp Connection is deleted and recreated...
				_configureSamlSpProperties(false, spConfig, virtualInstanceConfig, virtualInstanceFolderName, false, true);
				
				_deleteSpIdpConnections(company.getCompanyId(), virtualInstanceFolderName);
				
				long idpConfigCount = idPConfigs.size();
				long idpCreateSuccessCount = 0;
				
				for (IdPConfig idpConfig: idPConfigs) {
					boolean idpCreateSuccess = _createSamlSpIdpConnection(idpConfig, instanceFolder, virtualInstanceFolderName);
					
					if (idpCreateSuccess) idpCreateSuccessCount ++;
				}
				
				if (idpCreateSuccessCount != idpConfigCount) {
					_log.error(virtualInstanceFolderName + ": An error occurred recreating one of more IdP Connections.");
					
					_configureSamlSpProperties(false, spConfig, null, virtualInstanceFolderName, true, false);

					virtualInstanceErrorCount ++;
					virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
						
					continue;							
				}
				
				_configureSamlSpProperties(spConfig.isSamlEnabled(), spConfig, virtualInstanceConfig, virtualInstanceFolderName, false, false);

				_log.info(virtualInstanceFolderName + ": Finished processing SAML configuration for SP Entity ID " + spConfig.getSamlSpEntityId() + ".");
					
				virtualInstancesSAMLRestored.add(virtualInstanceFolderName);
			} catch (Exception e) {
				virtualInstanceErrorCount++;
				virtualInstancesSAMLRestoreUnsuccessful.add(virtualInstanceFolderName);
				
				_log.error(e.getClass() + ": " + e.getMessage());

				try {
					if (Validator.isNotNull(spConfig)) { // Deactivate SAML if we know an error occurred during processing..
						_configureSamlSpProperties(false, spConfig, null, virtualInstanceFolderName, true, false);
					}
				} catch (Exception ex) {
					_log.error(virtualInstanceFolderName + ": Error trying to disable SAML Configuration for SP Entity ID " + spConfig.getSamlSpEntityId() + ".");
					_log.error(ex.getClass() + ": " + ex.getMessage());
				}
				
				continue;
			}
		}
		
		StringBuffer sb = new StringBuffer();

		sb.append("SAML configurations restored: " + virtualInstancesSAMLRestored.size());
		sb.append("\n");
		_log.info("SAML configurations restored: " + virtualInstancesSAMLRestored.size());
			
		virtualInstancesSAMLRestored.forEach(
			i -> {
				_log.info(" > " + i + ", SAML has been restored successfully.");
				sb.append(" > " + i + ", SAML has been restored successfully.");
				sb.append("\n");
			});
			
		_log.info("SAML configurations not restored: " + virtualInstanceErrorCount);
		sb.append("SAML configurations not restored: " + virtualInstanceErrorCount);
		sb.append("\n");
			
		virtualInstancesSAMLRestoreUnsuccessful.forEach(
			i -> {
				_log.info(" > " + i + ", SAML has NOT been restored successfully.");
				sb.append(" > " + i + ", SAML has NOT been restored successfully.");
				sb.append("\n");
			});
			
		_log.info("Finished running restoreSamlConfig.");
		sb.append("Finished running restoreSamlConfig.");
		sb.append("\n");
		sb.append("Check the Liferay logs for more detailed information.");
		
		return sb.toString();
	}
	
	private String _idpPopertyPrefix(long pos) {
		return "idp" + pos + ".";
	}
	
	private boolean containsFile(File[] folderFiles, String fileName) {
		if (folderFiles == null) return false;
		
		return Arrays.stream(folderFiles).anyMatch(file -> fileName.equals(file.getName()));
	}
	
	private boolean _createSamlSpIdpConnection(
		IdPConfig idpConfig,
		File instanceFolder,
		String virtualInstanceFolderName) {

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setCompanyId(idpConfig.getCompanyId());
		
		InputStream metadataXMLInputStream = null;

		try {
			String metadataXMLFilePath = StringBundler.concat(instanceFolder, StringPool.FORWARD_SLASH, idpConfig.getMetadataXmlFileName());
			
			metadataXMLInputStream = new FileInputStream(metadataXMLFilePath);
			
			SamlSpIdpConnection samlSpIdpConnection =
				_samlSpIdpConnectionLocalService.addSamlSpIdpConnection(
					idpConfig.isAssertionSignatureRequired(),
					idpConfig.getClockSkew(),
					idpConfig.isEnabled(),
					idpConfig.isForceAuthn(),
					idpConfig.isLdapImportEnabled(),
					null, // Use of URL not supported 
					metadataXMLInputStream,
					idpConfig.getName(),
					idpConfig.getNameIdFormat(),
					idpConfig.getSamlIdpEntityId(),
					idpConfig.isSignAuthnRequest(),
					idpConfig.isUnknownUsersAreStrangers(),
					idpConfig.getUserAttributeMappings(),
					idpConfig.getUserIdentifierExpression(),
					serviceContext);

			_log.info(virtualInstanceFolderName + ": A new SAML IdP Connection " + samlSpIdpConnection.getName() + " for IdP Entity ID " + samlSpIdpConnection.getSamlIdpEntityId() + " has been successfully created.");
			
			return true;
			
		} catch (PortalException e) {
			_log.error(virtualInstanceFolderName + ": Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + ".");
			_log.error(e.getClass() + ": " + e.getMessage());
		} catch (FileNotFoundException e) {
			_log.error(virtualInstanceFolderName + ": Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + ".");
			_log.error(e.getClass() + ": " + e.getMessage());		
		} catch (Exception e) {
			_log.error(virtualInstanceFolderName + ": Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + ".");
			_log.error(e.getClass() + ": " + e.getMessage());		
		} finally {
			if (metadataXMLInputStream != null) {
				try {
					metadataXMLInputStream.close();
				} catch (IOException e) {}
			}
		}
		
		return false;
	}

	private void _configureSamlSpProperties(
			boolean enableSaml, SPConfig spConfig, VirtualInstanceSecretConfig virtualInstanceConfig, String virtualInstanceFolderName, boolean errorOccurred, boolean temporaryOperation)
		throws Exception {

		UnicodeProperties samlProperties =
			SamlRestoreToolUtil.calculateSamlProperties(
				enableSaml, spConfig, virtualInstanceConfig);
		
		_samlProviderConfigurationHelper.updateProperties(samlProperties);

		String status = enableSaml ? "enabled" : "disabled";

		if (errorOccurred) {
			_log.error(virtualInstanceFolderName + ": An error occurred, SAML has been deactivated for SP Entity ID " + spConfig.getSamlSpEntityId() + ".");
		} else {
			if (!temporaryOperation) {
				_log.info(virtualInstanceFolderName + ": The SAML SP Configuration has been successfully updated for SP Entity ID " + spConfig.getSamlSpEntityId() + " and SAML is now " + status + ".");
			}
		}
	}

	private void _deleteSpIdpConnections(long companyId, String virtualInstanceFolderName) {
		_samlSpIdpConnectionLocalService.getSamlSpIdpConnections(
			companyId
		).forEach(
			i -> {
				try {
					_samlSpIdpConnectionLocalService.deleteSamlSpIdpConnection(i.getSamlSpIdpConnectionId());
				} catch (PortalException e) {
					_log.error(virtualInstanceFolderName + ": Error while trying to delete the SAML IdP Connection with Name " + i.getName() + ".");
					_log.error(e.getClass() + ": " + e.getMessage());
				} catch (Exception e) {
					_log.error(virtualInstanceFolderName + ": Error while trying to delete the SAML IdP Connection with Name " + i.getName() + ".");
					_log.error(e.getClass() + ": " + e.getMessage());
				}
			}
		);
	}

	private KeyStore _replaceVirtualInstanceKeyStore(
		boolean hasEncryptionCert, String samlSpEntityId, File instanceFolder, String restorableKeyStoreFileName,
		VirtualInstanceSecretConfig virtualInstanceConfig, String virtualInstanceFolderName) {

		KeyStore restorableKeyStore = null;
		InputStream restorableKeyStoreInputStream = null;
		
		String restorableKeyStoreType = FileUtil.getExtension(restorableKeyStoreFileName);
		String restorableKeyStoreFilePath = StringBundler.concat(instanceFolder, StringPool.FORWARD_SLASH, restorableKeyStoreFileName);

		try {
			restorableKeyStore = KeyStore.getInstance(restorableKeyStoreType);
			
			restorableKeyStoreInputStream = new FileInputStream(restorableKeyStoreFilePath);
			
			String restorableKeyStoreFilePassword = virtualInstanceConfig.getKeyStorePassword();
			
			restorableKeyStore.load(restorableKeyStoreInputStream, restorableKeyStoreFilePassword.toCharArray());
			
			_log.info(virtualInstanceFolderName + ": The restorable SAML KeyStore has been successfully loaded for SP Entity ID " + samlSpEntityId + ".");
			
			// Replaces the contents of the Virtual Instance Doc Lib KeyStore with the contents of the provided KeyStore.
			// The existing Virtual Instance Doc Lib KeyStore password is NOT changed.
			// This doesn't seem to do anthing when File System Keystore in use...
			_keyStoreManager.saveKeyStore(restorableKeyStore);

			_log.info(virtualInstanceFolderName + ": The SAML KeyStore has been successfully updated for SP Entity ID " + samlSpEntityId + ".");	
		}
		catch (Exception e) {
			_log.error(virtualInstanceFolderName + ": Error calling _replaceVirtualInstanceKeyStore for SP Entity ID " + samlSpEntityId + ".");	
			_log.error(e.getClass() + ": " + e.getMessage());
			
			return null;
		} finally {
			if (restorableKeyStoreInputStream != null) {
				try {
					restorableKeyStoreInputStream.close();
				} catch (IOException e) {}
			}
		}

		KeyStore tempKeyStore = null;

		try {
			tempKeyStore = _keyStoreManager.getKeyStore();
		} catch (KeyStoreException e) {
			_log.error(e.getClass() + ": " + e.getMessage());
		} catch (Exception e) {
			_log.error(e.getClass() + ": " + e.getMessage());
		}

		boolean signingCertificateVerified = _verifySigningCertificate(samlSpEntityId, virtualInstanceConfig, tempKeyStore);
		
		if (signingCertificateVerified) {
			_log.info(virtualInstanceFolderName + ": The (Signing) Certificate and Private Key have been successfully verified for SP Entity ID " + samlSpEntityId + ".");
		} else {
			_log.info(virtualInstanceFolderName + ": The (Signing) Certificate and Private Key have NOT been verified for SP Entity ID " + samlSpEntityId + ".");
			
			return null;
		}
	
		// check if the encryption certificate exists
		if (hasEncryptionCert) {
			boolean encryptionCertificateVerified = _verifyEncryptionCertificate(samlSpEntityId, virtualInstanceConfig, tempKeyStore);
			
			if (encryptionCertificateVerified) {
				_log.info(virtualInstanceFolderName + ": The Encryption Certificate and Private Key have been successfully verified for SP Entity ID " + samlSpEntityId + ".");
			} else {
				_log.info(virtualInstanceFolderName + ": The Encryption Certificate and Private Key have NOT been verified for SP Entity ID " + samlSpEntityId + ".");
				
				return null;
			}
		}

		return restorableKeyStore;

	}

	private boolean _verifyEncryptionCertificate(String samlSpEntityId, VirtualInstanceSecretConfig virtualInstanceConfig, KeyStore newKeyStore) {

		try {
			Key key = newKeyStore.getKey(
				StringBundler.concat(
					samlSpEntityId, StringPool.DASH,
					LocalEntityManager.CertificateUsage.ENCRYPTION.name()),
				virtualInstanceConfig.getEncryptionCertificatePassword().toCharArray());
			
			if (key == null) return false;
			
			return true;
		} catch (Exception e) {
			_log.error(e.getClass() + ": " + e.getMessage());
			
			return false;
		}
	}

	private boolean _verifySigningCertificate(String samlSpEntityId, VirtualInstanceSecretConfig virtualInstanceConfig, KeyStore newKeyStore) {
		try {
			Key key = newKeyStore.getKey(samlSpEntityId, virtualInstanceConfig.getSigningCertificatePassword().toCharArray());
			
			if (key == null) return false;
			
			return true;
		} catch (Exception e) {
			_log.error(e.getClass() + ": " + e.getMessage());
			
			return false;
		}
	}
	
	private VirtualInstanceSecretConfig _getVirtualInstanceConfig(String secretParamValue) {
		
		VirtualInstanceSecretConfig virtualInstanceConfig = new VirtualInstanceSecretConfig();

		String keyStorePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_PARAM, secretParamValue));
		virtualInstanceConfig.setKeyStorePassword(keyStorePassword);
		
		String signingCertificatePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_PARAM, secretParamValue));
		virtualInstanceConfig.setSigningCertificatePassword(signingCertificatePassword);
		
		// Optional...
		String encryptionCertificatePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_PARAM, secretParamValue));
		virtualInstanceConfig.setEncryptionCertificatePassword(encryptionCertificatePassword);

		return virtualInstanceConfig;
	}
	
	private String getEnvironmentVariableName(String key, String secretParamValue) {
		if (Validator.isNull(key)) return key;
		
		String name = key.replace("{0}", secretParamValue);
	
		return name;
	}
	
	private CommonEnvironmentVariableConfig _getCommonConfig() {
		
		CommonEnvironmentVariableConfig commonConfig = new CommonEnvironmentVariableConfig();
		
		String enabledString = System.getenv(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.REGULAR.SAML_RESTORE_TOOL_ENABLED);
		
		boolean enabled = Boolean.valueOf(enabledString);
		
		commonConfig.setEnabled(enabled);
		
		// Don't bother loading the others if enabled is not true.
		if (!enabled) return commonConfig;

		String configPath = System.getenv(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.REGULAR.SAML_RESTORE_TOOL_CONFIG_PATH);
		commonConfig.setConfigPath(configPath);

		return commonConfig;
	}	

	private static final Log _log = LogFactoryUtil.getLog(SamlRestoreToolServiceImpl.class);
	
	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference(name = "KeyStoreManager", target = "(default=true)")
	private KeyStoreManager _keyStoreManager;
	
	@Reference
	private Portal _portal;

	@Reference
	private SamlProviderConfigurationHelper _samlProviderConfigurationHelper;

	@Reference
	private SamlSpIdpConnectionLocalService _samlSpIdpConnectionLocalService;	
}