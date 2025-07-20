package com.mw.saml.restore.tool.service.model;

import com.liferay.portal.kernel.util.Validator;

public class VirtualInstanceSecretConfig {
	
	public VirtualInstanceSecretConfig() {
		super();
	}
	
	public boolean isValid() {
		if (Validator.isNull(_keyStorePassword) || Validator.isNull(_signingCertificatePassword)) return false;
		
		return true;
	}

	public String getKeyStorePassword() {
		return _keyStorePassword;
	}
	public void setKeyStorePassword(String keyStorePassword) {
		this._keyStorePassword = keyStorePassword;
	}
	public String getSigningCertificatePassword() {
		return _signingCertificatePassword;
	}
	public void setSigningCertificatePassword(String signingCertificatePassword) {
		this._signingCertificatePassword = signingCertificatePassword;
	}
	public String getEncryptionCertificatePassword() {
		return _encryptionCertificatePassword;
	}
	public void setEncryptionCertificatePassword(String encryptionCertificatePassword) {
		this._encryptionCertificatePassword = encryptionCertificatePassword;
	}

	private String _keyStorePassword;
	private String _signingCertificatePassword;
	private String _encryptionCertificatePassword;
}