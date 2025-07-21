## Introduction ##
- We encourage customers to backup and restore prod data to non-prod enviornments regularly, however:
  - Liferay DXP stores SAML Admin configuration (General, Service Provider and Identity Provider Connections) in it's database.
  - Liferay DXP stores the SAML Admin Certificate and Private Key and Encryption Certificate and Private Key in either the Document Library or within the File System.
  - When a Liferay PaaS Backup is restored from for example prod to uat, the uat database is overwritten, including the SAML Admin configuration and the Keystore mappings etc.
- The SAML Restore Tool programatically restores a SAML configuration for Liferay as SAML Service Provider (SP) into a non-prod environment for example after a Liferay PaaS Backup from another environment is restored into that non-prod environment.
- The SAML Restore Tool is intended for use in Liferay PaaS but it can also be used outside of Liferay PaaS if the correct setup is in place in the environment.
- Having the option of restoring the previous SAML Configuration:
  - Ensures that the SAML SP Metadata XML doesn't change, meaning it doesn’t need to be reshared with the IdP team.
  - Allows prod and non-prod environments to use a separate SAML IdP if required.
  - Follows security good practice of prod and non-prod environments using separate Certificates and Private Keys etc.
  - Speeds up the Restore process by automating time consuming complex manual steps.

## Non-prod Liferay PaaS Environment Setup ##
- The following steps need to be completed once per non-prod environment (and then maintained for future SAML Admin configuration changes). The configuration files and restorable KeyStore become part of the Liferay PaaS build for the non-prod environment, so they won't get overwritten by a Backup being Restored from another environment into the non-prod environment.
- Create the following folder structure within the Liferay Service folder of the DXP Cloud Workspace:
  - configs/[ENV]/saml-restore-tool-config/virtual-instances
    - where [ENV] is a Liferay PaaS non-prod environment e.g. uat. **Do NOT create for prod or common...**
    - The DXP Cloud Workspace folder liferay/configs/[ENV]/saml-restore-tool-config/virtual-instances translates to /opt/liferay/saml-restore-tool-config/virtual-instances in the Liferay service shell.
  - Within the virtual-instances folder, create a subfolder for each SAML enabled Virtual Instance, using the case sensitive Virtual Instance Web ID e.g. configs/[ENV]/saml-restore-tool-config/virtual-instances/liferay.com
  - Inside each Virtual Instance Web ID folder add the following files:
    1. saml-admin-configuration.properties: A properties file containing the SAML Admin values to restore for this Virtual Instance. Start with the saml-restore-tool-config\saml-admin-configuration_TEMPLATE.properties file (from the repository), rename the file and update the values based on the table in the **saml-admin-configuration.properties** section. A sample file (saml-admin-configuration_SAMPLE.properties) is also included for reference.
  - 2. The SAML IdP Metadata XML file(s) e.g. idp-metadata-file.xml, using the same name as the corresponding *.idp.metadata.file property value in saml-admin-configuration.properties.
    - There should be one Metadata XML file per SAML Identify Provider defined in SAML Admin > Identity Provider Connections
  - 3. The KeyStore where the restorable Certificate and Private Key and optionally the Encryption Certificate and Private Key are stored, using the same name as the key.store.file property value in saml-admin-configuration.properties. See **Steps to setup the restorable KeyStore** section for steps to setup this KeyStore.
- Add the Environment Variables to the environments Liferay Service - see **Steps to setup the Environment Variables** section.
- Add the saml-restore-tool-service OSGi module source code to the DXP Cloud Workspace (within Liferay service modules folder) and confirm that the module successfully builds locally.
- Add the changes to the GIT repository, allow the Liferay PaaS INFRA environment CI service to generate a new Liferay PaaS build, then deploy that build in the non-prod Liferay PaaS environment.

## saml-admin-configuration.properties ##

| Property  | Field Type | Type | Description |
| -------- | ------- | ------- |  ------- |
| company.virtual.host | String | NA | The Virtual Host for the Virtual Instance. See Control Panel > System > Virtual Instances.|
| secret.param | String | NA | Used to map to the parameterised Environment Variables for this Virtual Instance. Must contain UPPER CASE and _ chars only. For example Virtual Instance Web ID liferay.com becomes LIFERAY_COM or mw-test.com becomes MW_TEST_COM etc.|
| saml.enabled | boolean | SP | SAML Admin > General > Enabled. Set this to false to restore the SAML configuration but not enable it. SAML can be manually enabled through the SAML Admin GUI afterwards.|
| saml.sp.entity.id | String | SP | SAML Admin > General > Entity ID. |
| key.store.file | String | SP | The restorable KeyStore file name. The KeyStore file must exist in the same folder as this properties file. |
| has.encryption.cert | boolean | SP | Whether or not SAML Admin > General > Encryption Certificate and Private Key is defined. |
| saml.sp.assertion.signature.required | boolean | SP | SAML Admin > Service Provider > Require Assertion Signature? |
| saml.sp.clock.skew | long | SP | SAML Admin > Service Provider > Clock Skew. |
| saml.sp.ldap.import.enabled | boolean | SP | SAML Admin > Service Provider > LDAP Import Enabled. |
| saml.sp.sign.authn.request | boolean | SP | SAML Admin > Service Provider > Sign Authn Requests? |
| saml.sign.metadata | boolean | SP | SAML Admin > Service Provider > Sign Metadata? |
| saml.ssl.required | boolean | SP | SAML Admin > Service Provider > SSL Required. |
| saml.sp.allow.showing.the.login.portlet | boolean | SP | SAML Admin > Service Provider > Allow showing the login portlet. |
| idp1.connection.name | String | IdP | SAML Admin > Identity Provider Connections > Connection X > Name. |
| idp1.saml.idp.entity.id | String | IdP | SAML Admin > Identity Provider Connections > Connection X > Entity ID. |
| idp1.connection.enabled | boolean | IdP | SAML Admin > Identity Provider Connections > Connection X > Enabled. |
| idp1.clock.skew | long | IdP | SAML Admin > Identity Provider Connections > Connection X > Clock Skew. |
| idp1.force.authn | boolean | IdP | SAML Admin > Identity Provider Connections > Connection X > Force Authn. |
| idp1.unknown.users.are.strangers | boolean | IdP | SAML Admin > Identity Provider Connections > Connection X > Unknown Users Are Strangers. |
| idp1.idp.metadata.file | String | IdP | The IdP Metadata XML filename for this IdP Connection. The XML file must exist in the same folder as this properties file. |
| idp1.name.id.format | String | IdP | Get from SamlSpIdPConnection table, nameIdFormat column value for this SAML IdP record. **\*** |
| idp1.user.attribute.mappings | String | IdP | Get from SamlSpIdPConnection table, userAttributeMappings column value for this SAML IdP record. **\*** |
| idp1.user.identifier.expression | String | IdP | Get from SamlSpIdPConnection table, userIdentifierExpression column value for this SAML IdP record. **\*** |
| idp1.assertion.signature.required | boolean | IdP | Get from SamlSpIdPConnection table, assertionSignatureRequired column value for this SAML IdP record. 0 means false, 1 means true. **\*** |
| idp1.ldap.import.enabled | boolean | IdP | Get from SamlSpIdPConnection table, ldapImportEnabled column value for this SAML IdP record. 0 means false, 1 means true. **\*** |
| idp1.sign.authn.request | boolean | IdP | Get from SamlSpIdPConnection table, forceAuthn column value for this SAML IdP record. 0 means false, 1 means true. **\*** |

- The SAML Restore Tool supports non-prod environments with multiple Virtual Instances as well as multiple SAML Identify Providers per Virtual Instance.
- Repeat the SAML Identity Provider properties with prefix **idp1.** for additional IdPs in the same Virtual Instance, using **idp2.** prefix for the second IdP, **idp3.** prefix for third IdP etc.
  - Note that properties in format connection.name and idp0.connection.name are not valid IdP property keys.
- Use the mysql or psql client from the non-prod environment Liferay service shell to check the SamlSpIdPConnection table values e.g.
  - select userAttributeMappings, userIdentifierExpression from SamlSpIdPConnection where name = 'MW IdP';

## Steps to setup the restorable KeyStore ##
- Use these steps to export the existing non-prod environment KeyStore for the Virtual Instance, so that the contents of the KeyStore can then be imported after the Backup and Restore has completed.
- Find the non-prod environment KeyStore in the environments Liferay service file system.
- With the 'Document Library Keystore Manager' it will be available in the Liferay service shell within the following folder: /opt/liferay/data/document_library/[COMPANY_ID]/0/saml/keystore.jks (where [COMPANY_ID] is the companyId for the relevant Virtual Instance).
  - For the Advanced File System Store the KeyStore filename within the 'keystore.jks' folder is 'keystore_1.0.jks'
  - For the Simple File Store the KeyStore filename within the 'keystore.jks' folder is '1.0'
- Download a Liferay PaaS Document Library Backup to extract the KeyStore OR use the LCP CLI Tool to download the KeyStore file after copying it to the persistent-storage location first.
- Download and install KeyStore Explorer on your local computer e.g. from https://keystore-explorer.org/
- Launch KeyStore Explorer on your local computer and click 'Open an existing KeyStore' then select the KeyStore from above. The default password for the KeyStore is liferay
- Verify that the KeyStore contains entries to match the Virtual Instance > SAML Admin setup:
  - An Entry Name of that matches the SP Entity Id for the 'Certificate and Private Key'
  - (Optionally) a second Entry Name that matches the SP Entity Id plus suffix '-encryption' for the 'Encryption Certificate and Private Key' (if enabled in the SAML Admin > General screen.)
- Select 'Set Password' from the KeyStore Explorer main menu and enter a new KeyStore Password and ensure the KeyStore is Saved after changing the Keystore Password.
  - Make a note of the new KeyStore Password as it will be used as the SAML_CONFIG_RESTORE_KEYSTORE_PASSWORD_{0} value for the Virtual Instance.

## Steps to setup the Environment Variables ##

- Add the following Environment Variables to the environments Liferay Service via the Liferay Service LCP.json.
  - The ones marked 'Per Virtual Instance' should be created for each SAML enabled Virtual Instance in the environment.
  	- Replace {0} with the secret.param value from saml-admin-configuration.properties _ e.g. a secret.param value of LIFERAY_COM would correspond to SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_LIFERAY_COM
  - The ones marked 'Secret' should first be defined as Secrets (in Liferay PaaS Environment > Settings > Secrets) then mapped to corresponding Environment Variables in the Liferay Service LCP.json using the @ syntax.
    - For example an existing Secret with the name 'saml-restore-tool-keystore-password-liferay.com' can be mapped in the LCP.json as follows:
    ```
    "SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_LIFERAY_COM": "@saml-restore-tool-keystore-password-liferay.com"
    ```
    - Note that the Secret name maximum length is 64 so the Secret name MAY need to be shortened, but the Environment Variable name MUST NOT be shortened.    

| Name | Mandatory | Secret | Per Virtual Instance | Description |
| -------- | ------- | ------- |  ------- | ------- |
| **SAML_RESTORE_TOOL_ENABLED** | Yes | No | No | Set to true to enable the functionality. If not populated or not set to true then the saml restore functionality won't run. |
| **SAML_RESTORE_TOOL_CONFIG_PATH** | Yes | No | No | The folder containing the restorable SAML configuration folders. The path is relative to liferay.home portal property, for example /saml-restore-tool-config/virtual-instances will be /opt/liferay/saml-restore-tool-config/virtual-instances in Liferay PaaS. |
| **SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_{0}** | Yes | Yes | Yes | Value is the new KeyStore Password from the **Steps to setup the 'restorable' KeyStore** section. |
| **SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_{0}** | Yes | Yes | Yes | The value is the original password for the Certificate and Private Key from SAML Admin > General. |
| **SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_{0}** | No | Yes | Yes | Only needed if the Virtual Instance SAML Admin has a Encryption Certificate and Private Key defined. Value is the original password for the Encryption Certificate and Private Key from SAML Admin > General. |

Sample Liferay service LCP.com extract for the uat environment with a single Virtual Instance with secret.param value of LIFERAY_COM using the @ secrets syntax:

```
    "uat": {
      "env": {
  	    "SAML_RESTORE_TOOL_ENABLED": "true",
  	    "SAML_RESTORE_TOOL_CONFIG_PATH": "/saml-restore-tool-config/virtual-instances",
  	    "SAML_RESTORE_TOOL_KEYSTORE_PASSWORD_LIFERAY_COM": "@saml-restore-tool-keystore-password-liferay.com",
  	    "SAML_RESTORE_TOOL_SIGNING_CERTIFICATE_PASSWORD_LIFERAY_COM": "@saml-restore-tool-signing-certificate-password-liferay.com",
  	    "SAML_RESTORE_TOOL_ENCRYPTION_CERTIFICATE_PASSWORD_LIFERAY_COM":"@saml-restore-tool-encryption-certificate-password-liferay.com"
      }
    }
```

## Triggering the SAML Restore Tool in a Liferay PaaS non-prod environment ##
- Ensure the steps in **Non-prod Environment Setup** have been completed in the non-prod environment before the Liferay PaaS backup is restored into the environment.
- Ensure a Liferay PaaS Build containing the SAML Restore Tool and it's configuration is in place in the non-prod environment. 
- After the Liferay PaaS Backup Restore into the target environment has successfully completed:
  - Go to the Liferay Service > Shell in the DXP Cloud Console (any Liferay DXP node in a HA environment)
  - Launch the Gogo Shell with telnet localhost:11311
  - Run the following custom Gogo shell command:
    - **samlRestoreTool:restoreSamlConfig**
  - Check the SAML Restore Tool logging output in the Liferay Service logs.
  - Manually update the SAML Admin > Identity Provider Connections > Connection > Keep Alive URL value through the Liferat SAML Admin GUI if this field is used. 
  - If the SAML Restore was successful then SAML SSO users should be able to login to the non-prod environment with the original SAML Admin configuration from before the Backup was Restored.
 
## Sample Success Logging Outout ##
```
[SamlRestoreToolServiceImpl:...] Started running restoreSamlConfig.
[SamlRestoreToolServiceImpl:...] Environment uses com.liferay.saml.opensaml.integration.internal.credential.DLKeyStoreManagerImpl
[SamlRestoreToolServiceImpl:...] liferay.com: Started processing SAML restore configuration.
[SamlRestoreToolServiceImpl:...] liferay.com: IdP count is 2.
[SamlRestoreToolServiceImpl:...] liferay.com: The restorable SAML KeyStore has been successfully loaded for SP Entity ID mw-sp.
[SamlRestoreToolServiceImpl:...] liferay.com: The SAML KeyStore has been successfully updated for SP Entity ID mw-sp.
[SamlRestoreToolServiceImpl:...] liferay.com: The Certificate and Private Key have been successfully verified for SP Entity ID mw-sp.
[SamlRestoreToolServiceImpl:...] liferay.com: The Encryption Certificate and Private Key have been successfully verified for SP Entity ID mw-sp.
[SamlRestoreToolServiceImpl:...] liferay.com: A new SAML IdP Connection Keycloak IdP 1 for IdP Entity ID http://localhost:8088/realms/mw has been successfully created.
[SamlRestoreToolServiceImpl:...] liferay.com: A new SAML IdP Connection Keycloak IdP 2 for IdP Entity ID http://mw.com:8088/realms/mw has been successfully created.
[SamlRestoreToolServiceImpl:...] liferay.com: The SAML SP Configuration has been successfully updated for SP Entity ID mw-sp and SAML is now enabled.
[SamlRestoreToolServiceImpl:...] liferay.com: Finished processing SAML configuration for SP Entity ID mw-sp.
[SamlRestoreToolServiceImpl:...] SAML configurations restored: 1
[SamlRestoreToolServiceImpl:...]  > liferay.com, SAML has been restored successfully.
[SamlRestoreToolServiceImpl:...] SAML configurations not restored: 0
[SamlRestoreToolServiceImpl:...] Finished running restoreSamlConfig.
```

## Using the SAML Restore Tool outside of Liferay PaaS ##
- The SAML Restore Tool can be used outside of Liferay PaaS as long as the environment variables and configuration files are setup for the environment with the correct paths etc.

## Future SAML Admin Changes after SAML Restore Tool Setup ##
- Any changes to the SAML Admin configuration should be applied in the configs/[ENV]/data/saml-restore-tool-config/virtual-instances/ setup and / or the secrets if applicable.
- Ensure the 'Certificate and Private Key' and 'Encryption Certificate and Private Key' in the Virtual Instance specific restorable KeyStore are updated when they are updated in SAML Admin > General screen e.g. after renewal etc.
- Ensure stored SAML Identity Provider Metadata XML file(s) are updated if the files change e.g. due to Certificate renewal or other setup change etc.

## Why not use SAML-Admin headless REST APIs? ##
- Liferay exposes some endpoints under SAML-Admin e.g. POST /v1.0/saml-provider with the description 'Creates a full SAML Provider configuration with peer connections.'.
- However the development of these SAML Admin endpoints was paused a few years ago, so this is a partially completed BETA feature, that does not currently handle the setup of the SAML Certificates and Private Keys.
- As such it is not a viable implementation approach for this use case.
 
## Assumptions ##
- SAML is already configured in the non-prod environment.
- SAML is configured using Control Panel > Security > SAML Admin.
- 'Certificate and Private Key' is always mandatory, but 'Encryption Certificate and Private Key' is optional.

## Known Limitations ##
- The SAML Restore Tool is designed for the use case of Liferay DXP acting as a SAML Service Provider (SP). The SAML Restore Tool is not intended for use in an environment where Liferay DXP is acting as a SAML Identity Provider (IdP).
- **ALL environments** (prod and non-prod) must already be using the Document Library Keystore Manager:
  - This can be checked in Control Panel > System Settings > Security > SSO > SAML KeyStoreManager Implementation Configuration > Keystore Manager Target. Ensure that Document Library Keystore Manager is in use. If not set then is is using the Filesystem Keystore Manager.
  - Document Library Keystore Manager is the recommended Keystore Manager for Liferay PaaS due to known issues with Filesystem Keystore Manager in a clustered environment: https://learn.liferay.com/l/33767483
  - Note that changing this setting once SAML has been configured will NOT copy the existing Certificates and Private Keys to the new Keystore. If it needs to be changed take a copy of the existing keystore (e.g. /opt/liferay/data/keystore.jks) and for each SAML enabled Virtual Instance:
    - Export > Export Key Pair in PKCS#12 format using KeyStore Exporter and Import after the Keystore Manager is changed using the SAML Admin > General GUI. Then test the SAML integration in the Virtual Instance to ensure it is still functioning as expected. Exporting and importing ensures the SAML will continue working without the need to share a new SP Metadata file with the IdP team etc.
- Liferay DXP stores the SAML Admin > Identity Provider Connections > Connection setting 'Keep Alive URL' value in the database as a Custom Field rather than in the SamlSpPdpConnection table.
  - As a result this field is NOT restored and it will reset to empty. If this field is required, it should be updated manually through the SAML Admin GUI after the Gogo shell command is run.
- The setup for the SAML Admin > Identity Provider Connections > Connection screen must use an IdP Metadata XML file, not an IdP Metadata URL.
  - A simple workaround is to download the IdP Metadata XML file from the IdP Metadata URL and include the XML file in the config folder for the Virtual Instance and reference it in the saml-admin-configuration.properties file.

## Environment ##
- The SAML Restore Tool has been tested with:
  -  Liferay DXP 2025.Q1.0 LTS (Liferay Workspace > gradle.properties > liferay.workspace.product = dxp-2025.q1.0-lts) and JDK 21 compile and runtime.
  -  Liferay DXP 2025.Q1.15 LTS (Liferay Workspace > gradle.properties > liferay.workspace.product = dxp-2025.q1.15-lts) and JDK 21 compile and runtime.
  - Liferay DXP 7.4 U92 (Liferay Workspace > gradle.properties > liferay.workspace.product = dxp-7.4-u92) with JDK11 compile and runtime.
- The SAML Restore Tool has also been tested in Liferay PaaS and Liferay Self Hosted.

## Notes ##
- This is a ‘proof of concept’ that is being provided ‘as is’ without any support coverage or warranty.
- The custom Gogo shell command and the related setup should not be used in a production environment. It is only intended for use in a non-production environment.
- It is recommended to use the Liferay Service LCP.json to manage the Environment Variables to prevent them being overwritten or lost e.g. if the Liferay Service is deleted and re-deployed.
- If the SAML Restore Tool doesn't succeed in updating a specific SAML Configuration it will attempt to set it as inactive.
- The Liferay service must be running for the Gogo Shell to be available.
- The SAML Restore Tool can be run before an Elasticsearch reindex has been triggered following a backup restore to the environment.
- Since the Virtual Instance specific restorable KeyStore is accessible to anyone with access to the DXP Cloud repository, the steps include changing the KeyStore password to keep the KeyStore secure.

## Reference ##
- https://keystore-explorer.org/
- https://learn.liferay.com/w/dxp/cloud/reference/command-line-tool
- https://learn.liferay.com/w/dxp/cloud/reference/command-line-tool#downloading-files-from-the-liferay-service
- https://learn.liferay.com/w/dxp/security-and-administration/users-and-permissions/roles-and-permissions/configuring-a-password-policy
- https://learn.liferay.com/w/dxp/security-and-administration/security/multi-factor-authentication
