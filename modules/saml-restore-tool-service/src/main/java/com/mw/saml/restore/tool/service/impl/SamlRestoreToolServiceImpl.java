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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.ArrayList;
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
	
	public void restoreSamlConfig() {
		_log.info("Started running restoreSamlConfig.");
		
		_log.info("Environment uses " + _keyStoreManager.getClass().getCanonicalName());

		if (_keyStoreManager != null && !_keyStoreManager.getClass().getCanonicalName().equalsIgnoreCase(SamlRestoreToolConstants.DOCUMENT_LIBRARY_KEYSTORE_MANAGER)) {
			_log.info("SAML KeyStoreManager Implementation Configuration > Keystore Manager Target not set to Document Library Keystore Manager. Not proceeding.");
	
			return;
		}
					
		CommonEnvironmentVariableConfig commonConfig = _getCommonConfig();
	
		if (!commonConfig.isEnabled()) {
			_log.info("SAML_CONFIG_RESTORE_ENABLED not set to true. Not proceeding.");

			return;
		}
		
		if (!commonConfig.isValid()) {
			_log.info("Environment Variables not configured as expected. Not proceeding.");

			return;
		}
		
		Properties portalProperties = _portal.getPortalProperties();

		String liferayHome = portalProperties.getProperty(PropsKeys.LIFERAY_HOME);
		String relativePathToFolder = commonConfig.getConfigPath();

		if (Validator.isNull(relativePathToFolder) || Validator.isNull(liferayHome)) {
			_log.info("Path configuration not as expected. Not proceeding.");

			return;
		}

		List<String> virtualInstancesSAMLRestored = new ArrayList<>();
		List<String> virtualInstancesSAMLNotUpdated = new ArrayList<>();
		int virtualInstanceErrorCount = 0;
		
		String configPath = liferayHome + relativePathToFolder;
		File configRootFolder = new File(configPath);

		if (configRootFolder.exists() && configRootFolder.isDirectory()) {
			for (File instanceFolder : configRootFolder.listFiles()) {
				if (instanceFolder.isDirectory() ) {
					String webIdFolderName = instanceFolder.getName();
					
					_log.info("Started processing SAML configuration for Web ID " + webIdFolderName);
					
					List<IdPConfig> idPConfigs = new ArrayList<IdPConfig>();
					long invalidIdPCount = 0;
					
					SPConfig spConfig = null;
					String virtualHost = null;
					Company company = null;
					
					try {
						Properties samlAdminConfigurationProperties = SamlRestoreToolUtil.loadPropertiesFile(instanceFolder.getPath() + StringPool.FORWARD_SLASH + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE);
						
						if (Validator.isNull(samlAdminConfigurationProperties)) {
							_log.info("Unable to load properties from " + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE + " for Web ID " + webIdFolderName + ".");
							
							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
							
							continue;								
						}
							
						//Basic Validation of properties before progressing...
						boolean isValidBasic = SamlRestoreToolUtil.isValidBasic(_idpPopertyPrefix(1), samlAdminConfigurationProperties);
						
						if (!isValidBasic) {
							_log.info("Basic validation of " + SamlRestoreToolConstants.SAML_ADMIN_CONFIGURATION_FILE + " failed for Web ID " + webIdFolderName + ".");
							
							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
							
							continue;								
						}
						
						virtualHost = samlAdminConfigurationProperties.getProperty(SamlRestoreToolConstants.COMPANY_VIRTUAL_HOST);
						
						if (!Validator.isNull(virtualHost)) {
							company = _companyLocalService.fetchCompanyByVirtualHost(virtualHost);
						}
							
						if (company == null) {
							_log.info("Company not found by fetchCompanyByVirtualHost for company.virtual.host " + virtualHost + " for Web ID " + webIdFolderName + ".");
							
							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;
						}
							
						String webId = company.getWebId();
							
						VirtualInstanceSecretConfig virtualInstanceConfig = _getVirtualInstanceConfig(webId);
							
						//Validate the secrets exist etc.
						if (!virtualInstanceConfig.isValid()) {
							_log.info("Mandatory secrets not configured as expected for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
							
							continue;
						}
						
						for (int i = 1; i <= 10; i++) { // Assume no more than 10...
							String dynamicPrefix = _idpPopertyPrefix(i);
						
							//Basic checks before loading fully and more detailed validation...
							String idPName = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.IDP.CONNECTION_NAME);
							String idPEntityId = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.IDP.SAML_IDP_ENTITY_ID);
							String idPMetadataFile = samlAdminConfigurationProperties.getProperty(dynamicPrefix + SamlRestoreToolConstants.IDP.IDP_METADATA_FILE);

							if (Validator.isNotNull(idPName) || Validator.isNotNull(idPEntityId) || Validator.isNotNull(idPMetadataFile)) {
								IdPConfig idpConfig = SamlRestoreToolUtil.parseIdPConfig(dynamicPrefix, company.getCompanyId(), samlAdminConfigurationProperties);
							
								//Basic Validation of this set of IdP properties
								if (idpConfig.isValidBasic()) {
									idPConfigs.add(idpConfig);
								} else {
									invalidIdPCount ++;
								}
							} else {
								break;
							}
						}
							
						_log.info("IdP count is " + idPConfigs.size() + " for Web ID " + webIdFolderName + ".");
						
						if (idPConfigs.isEmpty()) {
							_log.info("No IdP configurations in the properties file for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
							
							continue;								
						} else if (invalidIdPCount > 0) {
							_log.info("One of more invalid or incomplete IdP configurations in the properties file for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;									
						}

						spConfig = SamlRestoreToolUtil.parseSPConfig(samlAdminConfigurationProperties);

						if (spConfig.hasEncryptionCert() && Validator.isNull(virtualInstanceConfig.getEncryptionCertificatePassword())) {
							String encryptionCertificatePassword = SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_PARAM.replaceAll("\\{0\\}", webId);
								
							_log.info("Secret " + encryptionCertificatePassword + " missing for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;								
						}
							
						CompanyThreadLocal.setCompanyId(company.getCompanyId());

						if (_samlProviderConfigurationHelper.getSamlProviderConfiguration() == null || _samlProviderConfigurationHelper.getSamlProviderConfiguration().companyId() != company.getCompanyId()) {
							_log.info("SAML SP Configuration missing for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;								
						}
						
						
						if (!_samlProviderConfigurationHelper.isRoleSp()) {
							_log.info("Unexpected SAML Role for Web ID " + webIdFolderName + ".");

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;							
						}
						
						KeyStore virtualInstanceKeyStore = _replaceVirtualInstanceKeyStore(spConfig.hasEncryptionCert(), spConfig.getSamlSpEntityId(), instanceFolder, samlAdminConfigurationProperties, virtualInstanceConfig, webIdFolderName);

						if (Validator.isNull(virtualInstanceKeyStore)) {
							_log.error("An error occurred updating the KeyStore for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + ".");
							
							_configureSamlSpProperties(false, spConfig, null, webIdFolderName, true, false);

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;
						}

						// Disabled temporarily while the Idp Connection is deleted and recreated...
						_configureSamlSpProperties(false, spConfig, virtualInstanceConfig, webIdFolderName, false, true);
						
						_deleteSpIdpConnections(company.getCompanyId(), webIdFolderName);
						
						long idpConfigCount = idPConfigs.size();
						long idpCreateSuccessCount = 0;
						
						for (IdPConfig idpConfig: idPConfigs) {
							boolean idpCreateSuccess = _createSamlSpIdpConnection(idpConfig, instanceFolder, webIdFolderName);
							
							if (idpCreateSuccess) idpCreateSuccessCount ++;
						}
						
						if (idpCreateSuccessCount != idpConfigCount) {
							_log.error("An error occurred recreating one of more IdP Connections for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + ".");
							
							_configureSamlSpProperties(false, spConfig, null, webIdFolderName, true, false);

							virtualInstanceErrorCount ++;
							virtualInstancesSAMLNotUpdated.add(webIdFolderName);
								
							continue;							
						}
						
						_configureSamlSpProperties(spConfig.isSamlEnabled(), spConfig, virtualInstanceConfig, webIdFolderName, false, false);
	
						_log.info("Finished processing SAML configuration for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + ".");
							
						virtualInstancesSAMLRestored.add(webIdFolderName);
					} catch (Exception e) {
						virtualInstanceErrorCount++;
						virtualInstancesSAMLNotUpdated.add(webIdFolderName);
						
						_log.error(e.getClass() + ": " + e.getMessage());

						try {
							if (Validator.isNotNull(spConfig)) { // Deactivate SAML if we know an error occurred during processing..
								_configureSamlSpProperties(false, spConfig, null, webIdFolderName, true, false);
							}
						} catch (Exception ex) {
							_log.error("Error trying to disable SAML Configuration for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + ".");
							_log.error(ex.getClass() + ": " + ex.getMessage());
						}
						
						continue;
					}
				}
			}
		}

		_log.info("SAML configurations restored: " + virtualInstancesSAMLRestored.size());
			
		virtualInstancesSAMLRestored.forEach(
			i -> {
				_log.info(" > " + i + ", SAML has been restored successfully.");
			});
			
		_log.info("SAML configurations not restored: " + virtualInstanceErrorCount);
			
		virtualInstancesSAMLNotUpdated.forEach(
			i -> {
				_log.info(" > " + i + ", SAML has NOT been restored successfully.");
			});
			
		_log.info("Finished running restoreSamlConfig.");
	}
	
	private String _idpPopertyPrefix(long pos) {
		return "idp" + pos + ".";
	}
	
	private boolean _createSamlSpIdpConnection(
		IdPConfig idpConfig,
		File instanceFolder,
		String webIdFolderName) {

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

			_log.info("A new SAML IdP Connection " + samlSpIdpConnection.getName() + " for IdP Entity ID " + samlSpIdpConnection.getSamlIdpEntityId() + " has been successfully created for Web ID " + webIdFolderName + ".");
			
			return true;
			
		} catch (PortalException e) {
			_log.error("Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + " for Web ID " + webIdFolderName + ".");
			_log.error(e.getClass() + ": " + e.getMessage());
		} catch (FileNotFoundException e) {
			_log.error("Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + " for Web ID " + webIdFolderName + ".");
			_log.error(e.getClass() + ": " + e.getMessage());		
		} catch (Exception e) {
			_log.error("Unable to add IdP Connection with IdP Entity ID " + idpConfig.getSamlIdpEntityId() + " for Web ID " + webIdFolderName + ".");
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
			boolean enableSaml, SPConfig spConfig, VirtualInstanceSecretConfig virtualInstanceConfig, String webIdFolderName, boolean errorOccurred, boolean temporaryOperation)
		throws Exception {

		UnicodeProperties samlProperties =
			SamlRestoreToolUtil.calculateSamlProperties(
				enableSaml, spConfig, virtualInstanceConfig);
		
		_samlProviderConfigurationHelper.updateProperties(samlProperties);

		String status = enableSaml ? "enabled" : "disabled";

		if (errorOccurred) {
			_log.error("An error occurred, SAML has been deactivated for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + ".");
		} else {
			if (!temporaryOperation) {
				_log.info("The SAML SP Configuration has been successfully updated for SP Entity ID " + spConfig.getSamlSpEntityId() + " for Web ID " + webIdFolderName + " and SAML is now " + status + ".");
			}
		}
	}

	private void _deleteSpIdpConnections(long companyId, String webIdFolderName) {
		_samlSpIdpConnectionLocalService.getSamlSpIdpConnections(
			companyId
		).forEach(
			i -> {
				try {
					_samlSpIdpConnectionLocalService.deleteSamlSpIdpConnection(i.getSamlSpIdpConnectionId());
				} catch (PortalException e) {
					_log.error("Error while trying to delete the SAML IdP Connection with Name " + i.getName() + " for Web ID " + webIdFolderName + ".");
					_log.error(e.getClass() + ": " + e.getMessage());
				} catch (Exception e) {
					_log.error("Error while trying to delete the SAML IdP Connection with Name " + i.getName() + " for Web ID " + webIdFolderName + ".");
					_log.error(e.getClass() + ": " + e.getMessage());
				}
			}
		);
	}

	private KeyStore _replaceVirtualInstanceKeyStore(
		boolean hasEncryptionCert, String samlSpEntityId, File instanceFolder,
		Properties properties, VirtualInstanceSecretConfig virtualInstanceConfig, String webIdFolderName) {

		KeyStore restorableKeyStore = null;
		InputStream restorableKeyStoreInputStream = null;
		
		String restorableKeyStoreFileName = properties.getProperty(SamlRestoreToolConstants.KEY_STORE_FILE);
		String restorableKeyStoreType = FileUtil.getExtension(restorableKeyStoreFileName);
		String restorableKeyStoreFilePath = StringBundler.concat(instanceFolder, StringPool.FORWARD_SLASH, restorableKeyStoreFileName);

		try {
			restorableKeyStore = KeyStore.getInstance(restorableKeyStoreType);
			
			restorableKeyStoreInputStream = new FileInputStream(restorableKeyStoreFilePath);
			
			String restorableKeyStoreFilePassword = virtualInstanceConfig.getKeyStorePassword();
			
			restorableKeyStore.load(restorableKeyStoreInputStream, restorableKeyStoreFilePassword.toCharArray());
			
			_log.info("The restorable SAML KeyStore has been successfully loaded for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");
			
			// Replaces the contents of the Virtual Instance Doc Lib KeyStore with the contents of the provided KeyStore.
			// The existing Virtual Instance Doc Lib KeyStore password is NOT changed.
			// This doesn't seem to do anthing when File System Keystore in use...
			_keyStoreManager.saveKeyStore(restorableKeyStore);

			_log.info("The SAML KeyStore has been successfully updated for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");	
		}
		catch (Exception e) {
			_log.error("Error calling _replaceVirtualInstanceKeyStore for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");	
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
			_log.info("The (Signing) Certificate and Private Key have been successfully verified for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");
		} else {
			_log.info("The (Signing) Certificate and Private Key have NOT been verified for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");
			
			return null;
		}
	
		// check if the encryption certificate exists
		if (hasEncryptionCert) {
			boolean encryptionCertificateVerified = _verifyEncryptionCertificate(samlSpEntityId, virtualInstanceConfig, tempKeyStore);
			
			if (encryptionCertificateVerified) {
				_log.info("The Encryption Certificate and Private Key have been successfully verified for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");
			} else {
				_log.info("The Encryption Certificate and Private Key have NOT been verified for SP Entity ID " + samlSpEntityId + " for Web ID " + webIdFolderName + ".");
				
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
	
	private VirtualInstanceSecretConfig _getVirtualInstanceConfig(String webId) {
		
		VirtualInstanceSecretConfig virtualInstanceConfig = new VirtualInstanceSecretConfig(webId);

		String keyStorePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_PARAM, webId));
		virtualInstanceConfig.setKeyStorePassword(keyStorePassword);
		
		String signingCertificatePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_PARAM, webId));
		virtualInstanceConfig.setSigningCertificatePassword(signingCertificatePassword);
		
		// Optional...
		String encryptionCertificatePassword = System.getenv(getEnvironmentVariableName(SamlRestoreToolConstants.ENVIRONMENT_VARIABLES.SECRET.SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_PARAM, webId));
		virtualInstanceConfig.setEncryptionCertificatePassword(encryptionCertificatePassword);

		return virtualInstanceConfig;
	}
	
	private String getEnvironmentVariableName(String constant, String webId) {
		if (Validator.isNull(constant)) return constant;
		
		String name = constant.replace("{0}", webId).replace(".", "_").toUpperCase();
	
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