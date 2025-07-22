package com.mw.saml.restore.tool.service.model;

import com.liferay.portal.kernel.util.Validator;

public class SPConfig {

	public SPConfig(
		boolean requireAssertionSignature, long clockSkew,
		boolean ldapImportEnabled, boolean signAuthnRequests,
		boolean signMetadata, boolean sslRequired,
		boolean allowShowingLoginPortlet, boolean hasEncryptionCertificate,
		boolean samlEnabled, String samlSpEntityId) {

		_requireAssertionSignature = requireAssertionSignature;
		_clockSkew = clockSkew;
		_ldapImportEnabled = ldapImportEnabled;
		_signAuthnRequests = signAuthnRequests;
		_signMetadata = signMetadata;
		_sslRequired = sslRequired;
		_allowShowingLoginPortlet = allowShowingLoginPortlet;
		_hasEncryptionCertificate = hasEncryptionCertificate;
		_samlEnabled = samlEnabled;
		_samlSpEntityId = samlSpEntityId;
	}

	public boolean isValidBasic() {
		if (Validator.isNull(_samlSpEntityId)) return false;	
		
		return true;
	}

	public boolean isRequireAssertionSignature() {
		return _requireAssertionSignature;
	}
	
	public long getClockSkew() {
		return _clockSkew;
	}
	
	public boolean isLdapImportEnabled() {
		return _ldapImportEnabled;
	}

	public boolean isSignAuthnRequests() {
		return _signAuthnRequests;
	}

	public boolean isSignMetadata() {
		return _signMetadata;
	}	
	
	public boolean isSslRequired() {
		return _sslRequired;
	}	

	public boolean isAllowShowingLoginPortlet() {
		return _allowShowingLoginPortlet;
	}
	
	public boolean hasEncryptionCertificate() {
		return _hasEncryptionCertificate;
	}

	public boolean isSamlEnabled() {
		return _samlEnabled;
	}

	public String getSamlSpEntityId() {
		return _samlSpEntityId;
	}

	private boolean _requireAssertionSignature;
	private long _clockSkew;
	private boolean _ldapImportEnabled;
	private boolean _signAuthnRequests;
	private boolean _signMetadata;
	private boolean _sslRequired;
	private boolean _allowShowingLoginPortlet;
	private boolean _hasEncryptionCertificate;
	private boolean _samlEnabled;
	private String _samlSpEntityId;
}