package com.mw.saml.restore.tool.service.model;

import com.liferay.portal.kernel.util.Validator;

public class IdPConfig {

	public IdPConfig() {
	}

	public IdPConfig(
		long companyId,
		boolean assertionSignatureRequired, long clockSkew, boolean enabled,
		boolean forceAuthn, boolean ldapImportEnabled, String metadataUrl, String metadataXmlFileName,
		String name, String samlIdpEntityId, String nameIdFormat, boolean signAuthnRequest,
		String userAttributeMappings,
		boolean unknownUsersAreStrangers, String userIdentifierExpression) {

		_companyId = companyId;
		_assertionSignatureRequired = assertionSignatureRequired;
		_clockSkew = clockSkew;
		_enabled = enabled;
		_forceAuthn = forceAuthn;
		_ldapImportEnabled = ldapImportEnabled;
		_metadataXmlFileName = metadataXmlFileName;
		_name = name;
		_samlIdpEntityId = samlIdpEntityId;
		_nameIdFormat = nameIdFormat;
		_signAuthnRequest = signAuthnRequest;
		_userAttributeMappings = userAttributeMappings;
		_unknownUsersAreStrangers = unknownUsersAreStrangers;
		_userIdentifierExpression = userIdentifierExpression;
	}
	
	public boolean isValidBasic() {
		if (Validator.isNull(_metadataXmlFileName)) return false;	
		
		if (Validator.isNull(_name)) return false;	
		
		if (Validator.isNull(_samlIdpEntityId)) return false;
		
		if (Validator.isNull(_nameIdFormat)) return false;
		
		if (Validator.isNull(_userAttributeMappings)) return false;
		
		// This is always populated, it is based on the 'User Resolution' radio buttons.
		// Values are none, dynamic or attribute:screenName or attribute:emailAddress etc.
		if (Validator.isNull(_userIdentifierExpression)) return false;
		
		return true;
	}

	public long getCompanyId() {
		return _companyId;
	}

	public boolean isAssertionSignatureRequired() {
		return _assertionSignatureRequired;
	}
	
	public long getClockSkew() {
		return _clockSkew;
	}

	public boolean isEnabled() {
		return _enabled;
	}

	public boolean isForceAuthn() {
		return _forceAuthn;
	}

	public boolean isLdapImportEnabled() {
		return _ldapImportEnabled;
	}

	public String getMetadataXmlFileName() {
		return _metadataXmlFileName;
	}

	public String getName() {
		return _name;
	}
	
	public String getSamlIdpEntityId() {
		return _samlIdpEntityId;
	}

	public String getNameIdFormat() {
		return _nameIdFormat;
	}

	public boolean isSignAuthnRequest() {
		return _signAuthnRequest;
	}
	
	public String getUserAttributeMappings() {
		return _userAttributeMappings;
	}
	
	public boolean isUnknownUsersAreStrangers() {
		return _unknownUsersAreStrangers;
	}
	
	public String getUserIdentifierExpression() {
		return _userIdentifierExpression;
	}
	
	private long _companyId;
	private boolean _assertionSignatureRequired;
	private long _clockSkew;
	private boolean _enabled;
	private boolean _forceAuthn;
	private boolean _ldapImportEnabled;
	private String _metadataXmlFileName;
	private String _name;
	private String _samlIdpEntityId;
	private String _nameIdFormat;
	private boolean _signAuthnRequest;
	private String _userAttributeMappings;
	private boolean _unknownUsersAreStrangers;
	private String _userIdentifierExpression;
}