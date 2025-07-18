package com.mw.saml.restore.tool.service.model;

import com.liferay.portal.kernel.util.Validator;

public class CommonEnvironmentVariableConfig {
	
	public CommonEnvironmentVariableConfig() {
		super();
	}

	public CommonEnvironmentVariableConfig(boolean enabled, String configPath) {
		super();
		this._enabled = enabled;
		this._configPath = configPath;
	}
	
	public boolean isValid() {
		if (Validator.isNull(_configPath)) return false;
		
		return true;
	}
	
	public boolean isEnabled() {
		return _enabled;
	}
	public void setEnabled(boolean enabled) {
		this._enabled = enabled;
	}
	public String getConfigPath() {
		return _configPath;
	}
	public void setConfigPath(String configPath) {
		this._configPath = configPath;
	}

	private boolean _enabled = false;
	private String _configPath;
}