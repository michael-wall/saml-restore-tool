package com.mw.saml.restore.tool.service.util;

import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.saml.constants.SamlProviderConfigurationKeys;
import com.liferay.saml.util.PortletPropsKeys;
import com.mw.saml.restore.tool.service.constants.SamlRestoreToolConstants;
import com.mw.saml.restore.tool.service.model.IdPConfig;
import com.mw.saml.restore.tool.service.model.SPConfig;
import com.mw.saml.restore.tool.service.model.VirtualInstanceSecretConfig;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class SamlRestoreToolUtil {

	public static UnicodeProperties calculateSamlProperties(
		boolean enableSaml, SPConfig spConfig,
		VirtualInstanceSecretConfig virtualInstanceConfig) {

		UnicodeProperties unicodeProperties = new UnicodeProperties();

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_ENTITY_ID,
			spConfig.getSamlSpEntityId());
		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_ROLE,
			SamlProviderConfigurationKeys.SAML_ROLE_SP);

		if (Validator.isNotNull(virtualInstanceConfig)) {
			unicodeProperties.setProperty(
				PortletPropsKeys.SAML_KEYSTORE_CREDENTIAL_PASSWORD,
				virtualInstanceConfig.getSigningCertificatePassword());

			if (spConfig.hasEncryptionCert()) {
				unicodeProperties.setProperty(
					PortletPropsKeys.
						SAML_KEYSTORE_ENCRYPTION_CREDENTIAL_PASSWORD,
					virtualInstanceConfig.getEncryptionCertificatePassword());
			}
		}

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SP_ASSERTION_SIGNATURE_REQUIRED,
			Boolean.toString(
				spConfig.isRequireAssertionSignature()));
		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SP_CLOCK_SKEW,
			String.valueOf(spConfig.getClockSkew()));

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SP_LDAP_IMPORT_ENABLED,
			Boolean.toString(spConfig.isLdapImportEnabled()));

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SP_SIGN_AUTHN_REQUEST,
			Boolean.toString(spConfig.isSignAuthnRequests()));

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SIGN_METADATA,
			Boolean.toString(spConfig.isSignMetadata()));

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SSL_REQUIRED,
			Boolean.toString(spConfig.isSslRequired()));

		unicodeProperties.setProperty(
			PortletPropsKeys.SAML_SP_ALLOW_SHOWING_THE_LOGIN_PORTLET,
			Boolean.toString(spConfig.isAllowShowingLoginPortlet()));

		if (enableSaml) {
			unicodeProperties.setProperty(
				PortletPropsKeys.SAML_ENABLED, StringPool.TRUE);
		}
		else {
			unicodeProperties.setProperty(
				PortletPropsKeys.SAML_ENABLED, StringPool.FALSE);
		}

		return unicodeProperties;
	}

	public static Properties loadPropertiesFile(String path) {
		
		InputStream propertiesInputStream = null;
		
		try {
			Properties properties = new Properties();
			propertiesInputStream = new FileInputStream(path);

			properties.load(propertiesInputStream);

			return properties;
		} catch (FileNotFoundException e) {
			_log.error(e.getClass() + ": " + e.getMessage());	
		} catch (IOException e) {
			_log.error(e.getClass() + ": " + e.getMessage());
		} catch (Exception e) {
			_log.error(e.getClass() + ": " + e.getMessage());				
		} finally {
			if (propertiesInputStream != null) {
				try {
					propertiesInputStream.close();
				} catch (IOException e) {}
			}
		}

		return null;
	}
	
	public static boolean isValidBasic(String idpPrefix, Properties properties) {
		
		// Mapping
		String virtualHost = properties.getProperty(SamlRestoreToolConstants.PROPERTIES.MAPPING.COMPANY_VIRTUAL_HOST);
		
		if (Validator.isNull(virtualHost)) return false;
		
		// Mapping
		String secretParam = properties.getProperty(SamlRestoreToolConstants.PROPERTIES.MAPPING.SECRET_PARAM);
		
		if (!isValidSecretParam(secretParam)) return false;

		//SP
		String samlSpEntityId = properties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.SAML_SP_ENTITY_ID);
		
		if (Validator.isNull(samlSpEntityId)) return false;
		
		//KeyStore
		String keyStoreFileName = properties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.KEY_STORE_FILE);
		
		if (Validator.isNull(keyStoreFileName)) return false;
		
		//IDP
		String idpName = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.CONNECTION_NAME);
		
		if (Validator.isNull(idpName)) return false;
		
		String idpMetadataXmlFile = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.IDP_METADATA_FILE);
		
		if (Validator.isNull(idpMetadataXmlFile)) return false;
		
		String idpSamlIdpEntityId = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.SAML_IDP_ENTITY_ID);
		
		if (Validator.isNull(idpSamlIdpEntityId)) return false;

		String idpUserAttributeMappings = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.USER_ATTRIBUTE_MAPPINGS);
		
		if (Validator.isNull(idpUserAttributeMappings)) return false;
		
		return true;
	}

	public static IdPConfig
			parseIdPConfig(String idpPrefix, long companyId, Properties properties)
		throws NumberFormatException, PortalException {

		boolean assertionSignatureRequired = GetterUtil.get(idpPrefix + properties.getProperty(SamlRestoreToolConstants.PROPERTIES.IDP.ASSERTION_SIGNATURE_REQUIRED), false);
		long clockSkew = GetterUtil.get(properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.CLOCK_SKEW), SamlRestoreToolConstants.PROPERTIES.SHARED.DEFAULT_CLOCK_SKEW_VALUE);
		boolean enabled = GetterUtil.get(properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.CONNECTION_ENABLED), false);
		boolean forceAuthn = GetterUtil.get(properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.FORCE_AUTHN), false);
		boolean ldapImportEnabled = GetterUtil.get(properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.LDAP_IMPORT_ENABLE), false);
		String metadataXmlFile = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.IDP_METADATA_FILE);
		String name = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.CONNECTION_NAME);
		String samlIdpEntityId = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.SAML_IDP_ENTITY_ID);
		String nameIdFormat = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.NAME_ID_FORMAT);
		boolean signAuthnRequest = GetterUtil.get(idpPrefix + properties.getProperty(SamlRestoreToolConstants.PROPERTIES.IDP.SIGN_AUTHN_REQUEST), false);
		String userAttributeMappings = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.USER_ATTRIBUTE_MAPPINGS);
		boolean unknownUsersAreStrangers = GetterUtil.get(idpPrefix + properties.getProperty(SamlRestoreToolConstants.PROPERTIES.IDP.UNKNOWN_USERS_ARE_STRANGERS), false);
		String userIdentifierExpression = properties.getProperty(idpPrefix + SamlRestoreToolConstants.PROPERTIES.IDP.USER_IDENTIFIER_EXPRESSION);		

		return new IdPConfig(
			companyId, assertionSignatureRequired, clockSkew,
			enabled, forceAuthn, ldapImportEnabled, null, metadataXmlFile, name, samlIdpEntityId, nameIdFormat,
			signAuthnRequest, userAttributeMappings, unknownUsersAreStrangers, userIdentifierExpression);
	}

	public static SPConfig parseSPConfig(
		Properties properties) {

		boolean requireAssertionSignature = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SP_ASSERTION_SIGNATURE_REQUIRED), false);
		long clockSkew = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SP_CLOCK_SKEW), SamlRestoreToolConstants.PROPERTIES.SHARED.DEFAULT_CLOCK_SKEW_VALUE);
		boolean ldapImportEnabled = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SP_LDAP_IMPORT_ENABLED), false);
		boolean signAuthnRequests = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SP_SIGN_AUTHN_REQUEST), false);
		boolean signMetadata = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SIGN_METADATA), false);
		boolean sslRequired = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SSL_REQUIRED), false);
		boolean allowShowingLoginPortlet = GetterUtil.get(properties.getProperty(PortletPropsKeys.SAML_SP_ALLOW_SHOWING_THE_LOGIN_PORTLET), false);
		boolean hasEncryptionCert = GetterUtil.get(properties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.HAS_ENCRYPTION_CERT), true);
		boolean samlEnabled = GetterUtil.get(properties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.SAML_ENABLED), false);
		String samlSpEntityId = properties.getProperty(SamlRestoreToolConstants.PROPERTIES.SP.SAML_SP_ENTITY_ID);

		return new SPConfig(
			requireAssertionSignature, clockSkew, ldapImportEnabled,
			signAuthnRequests, signMetadata, sslRequired,
			allowShowingLoginPortlet, hasEncryptionCert, samlEnabled,
			samlSpEntityId);
	}
	
	private static boolean isValidSecretParam(String key) {
        if (Validator.isNull(key)) {
            return false;
        }

        // Only uppercase letters and underscores allowed
        if (!key.matches("[A-Z_]+")) {
            return false;
        }

        // Must not start or end with an underscore
        if (key.startsWith("_") || key.endsWith("_")) {
            return false;
        }

        // Must not contain double underscores
        if (key.contains("__")) {
            return false;
        }

        return true;		
	}
	
	private static final Log _log = LogFactoryUtil.getLog(SamlRestoreToolUtil.class);
}