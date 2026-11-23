; Inno-Setup-Skript für den Windows-Installer. Aufruf über scripts\package.ps1, das Version und App-Image übergibt:
;   ISCC.exe /DVersion=0.1.0 /DBild=<pfad zum App-Image> packaging\openbilling.iss
; Installiert ohne Administratorrechte nach %LOCALAPPDATA%\Programs\openbilling (wahlweise für alle Benutzer),
; legt einen Startmenü-Eintrag an, bietet openbilling unter "Öffnen mit" für .ods an (ohne LibreOffice die
; Standardzuordnung wegzunehmen) und trägt sich unter "Apps" zum Deinstallieren ein.
; Updates: neuen Installer starten. Die AppId bleibt gleich, daher ersetzt er die alte Version im selben Ordner.

#ifndef Version
  #error "Version fehlt: /DVersion=<x.y.z>"
#endif
#ifndef Bild
  #error "App-Image fehlt: /DBild=<ordner mit openbilling.exe>"
#endif

[Setup]
; AppId nie ändern, sonst erkennt ein neuer Installer die alte Installation nicht mehr.
AppId={{73B228D0-7F26-4699-AA12-02B10741C357}
AppName=openbilling
AppVersion={#Version}
AppVerName=openbilling {#Version}
AppPublisher=openbilling
AppPublisherURL=https://github.com/Fridoodle/openbilling
AppSupportURL=https://github.com/Fridoodle/openbilling/issues
AppUpdatesURL=https://github.com/Fridoodle/openbilling/releases
VersionInfoVersion={#Version}
DefaultDirName={autopf}\openbilling
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
ChangesAssociations=yes
CloseApplications=yes
RestartApplications=no
UninstallDisplayIcon={app}\openbilling.exe
UninstallDisplayName=openbilling
OutputDir=..\dist
OutputBaseFilename=openbilling-{#Version}-windows-x64-setup
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ShowLanguageDialog=no

[Languages]
Name: "de"; MessagesFile: "compiler:Languages\German.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[InstallDelete]
; Bei einem Update alte Bibliotheken und Laufzeit vollständig entfernen, damit keine veralteten JARs liegen bleiben.
Type: filesandordirs; Name: "{app}\app"
Type: filesandordirs; Name: "{app}\runtime"

[Files]
Source: "{#Bild}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\openbilling"; Filename: "{app}\openbilling.exe"
Name: "{autodesktop}\openbilling"; Filename: "{app}\openbilling.exe"; Tasks: desktopicon

[Registry]
; Eigene Dateityp-Klasse, die "Öffnen mit" für .ods anbietet. Die Standardzuordnung von .ods bleibt unberührt.
Root: HKA; Subkey: "Software\Classes\openbilling.ods"; ValueType: string; ValueData: "Rechnung (openbilling)"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\openbilling.ods\DefaultIcon"; ValueType: string; ValueData: "{app}\openbilling.exe,0"
Root: HKA; Subkey: "Software\Classes\openbilling.ods\shell\open\command"; ValueType: string; ValueData: """{app}\openbilling.exe"" ""%1"""
Root: HKA; Subkey: "Software\Classes\.ods\OpenWithProgids"; ValueType: string; ValueName: "openbilling.ods"; ValueData: ""; Flags: uninsdeletevalue
; Anzeigename und unterstützte Typen im "Öffnen mit"-Dialog
Root: HKA; Subkey: "Software\Classes\Applications\openbilling.exe"; ValueType: string; ValueName: "FriendlyAppName"; ValueData: "openbilling"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\Applications\openbilling.exe\SupportedTypes"; ValueType: string; ValueName: ".ods"; ValueData: ""
Root: HKA; Subkey: "Software\Classes\Applications\openbilling.exe\shell\open\command"; ValueType: string; ValueData: """{app}\openbilling.exe"" ""%1"""

[Run]
Filename: "{app}\openbilling.exe"; Description: "{cm:LaunchProgram,openbilling}"; Flags: nowait postinstall skipifsilent

; Einstellungen, Vorlagen und Adressbuch unter %APPDATA%\openbilling bleiben beim Deinstallieren absichtlich erhalten.
