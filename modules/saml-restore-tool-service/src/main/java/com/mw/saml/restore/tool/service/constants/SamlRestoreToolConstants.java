package com.mw.saml.restore.tool.service.constants;

public class SamlRestoreToolConstants {
	
	public static final String DOCUMENT_LIBRARY_KEYSTORE_MANAGER = "com.liferay.saml.opensaml.integration.internal.credential.DLKeyStoreManagerImpl";
	
	public interface ENVIRONMENT_VARIABLES {
		public interface REGULAR {
			public static final String SAML_RESTORE_TOOL_ENABLED = "SAML_RESTORE_TOOL_ENABLED";
			
			public static final String SAML_RESTORE_TOOL_CONFIG_PATH = "SAML_RESTORE_TOOL_CONFIG_PATH";
		}
		
		public interface SECRET {
			public static final String SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_PARAM = "SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_{0}";
			
			public static final String SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_PARAM = "SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_{0}";
			
			public static final String SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_PARAM = "SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_{0}";
		}
	}
	
	public interface SP {
		public static final String SAML_ENABLED = "saml.enabled";
		
		public static final String SAML_SP_ENTITY_ID = "saml.sp.entity.id";
		
		public static final String HAS_ENCRYPTION_CERT = "has.encryption.cert";
	}

	public interface IDP {
		public static final String ASSERTION_SIGNATURE_REQUIRED = "assertion.signature.required";
		
		public static final String CLOCK_SKEW = "clock.skew";
		
		public static final String CONNECTION_ENABLED = "connection.enabled";
		
		public static final String FORCE_AUTHN = "force.authn";
		
		public static final String LDAP_IMPORT_ENABLE = "ldap.import.enabled";	
		
		public static final String IDP_METADATA_FILE = "idp.metadata.file";
		
		public static final String CONNECTION_NAME = "connection.name";
		
		public static final String SAML_IDP_ENTITY_ID = "saml.idp.entity.id";
		
		public static final String NAME_ID_FORMAT = "name.id.format";
		
		public static final String SIGN_AUTHN_REQUEST = "sign.authn.request";
	
		public static final String UNKNOWN_USERS_ARE_STRANGERS = "unknown.users.are.strangers";

		public static final String USER_ATTRIBUTE_MAPPINGS = "user.attribute.mappings";
		
		public static final String USER_IDENTIFIER_EXPRESSION = "user.identifier.expression";
	}


	public interface SHARED {
		public static final long DEFAULT_CLOCK_SKEW_VALUE = 3000L;
	}
	
	public static final String COMPANY_VIRTUAL_HOST = "company.virtual.host";

	public static final String SAML_ADMIN_CONFIGURATION_FILE = "saml-admin-configuration.properties";

	public static final String KEY_STORE_FILE = "key.store.file";
}