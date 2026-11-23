# XRechnung – Wissensbasis für openbilling

> Stand der Recherche: **23.11.2026**. Zielgruppe: KI-Coding-Agenten, die in openbilling Rechnungen erfassen (ODS), XRechnung-XML erzeugen und ein PDF/A-3 mit eingebettetem XML exportieren.
> Alles mit **(unverifiziert)** konnte nicht in einer Primärquelle bestätigt werden und muss vor der Umsetzung geprüft werden.

---

## 1. Was ist XRechnung?

- **XRechnung** ist die deutsche **CIUS** (Core Invoice Usage Specification) der europäischen Norm **EN 16931-1:2017** (semantisches Datenmodell) samt **CEN/TS 16931-2/-3** (Syntax-Bindings). Herausgeber: **KoSIT** (Koordinierungsstelle für IT-Standards) im Auftrag des IT-Planungsrats. Spezifikation und Bundles: xeinkauf.de.
- Sie schränkt EN 16931 ein: zusätzliche Pflichtfelder und Regeln (`BR-DE-*`). Jede gültige XRechnung ist auch eine gültige EN-16931-Rechnung, aber nicht umgekehrt.
- Im B2G-Bereich (öffentliche Auftraggeber in Bund und Ländern) ist sie Pflicht. Seit 2025 ist sie im B2B-Bereich ein zulässiges E-Rechnungsformat nach § 14 UStG (siehe 6.4).

### Versionen (Quelle: xeinkauf.de „Versionen und Bundles“)

| Version | Fassung vom | Gültigkeit |
|---|---|---|
| **3.0.2 (aktuell)** | 20.06.2024 | ab 20.06.2024; Version 3.0 gilt seit 01.02.2024 und laut KoSIT **mindestens bis 31.07.2027** |
| 3.0.1 | 22.09.2023 | ab 01.02.2024 |
| 2.3.1 | 03.02.2023 | 01.08.2023 – 31.01.2024 (abgelaufen) |
| 4.0.0 (Vorversion) | 15.09.2026 | **nicht produktiv nutzen.** Das finale Bundle wird „voraussichtlich im ersten Frühjahr 2027“ erwartet und basiert auf der überarbeiteten EN 16931 (u. a. > 50 neue BTs, BG-34 für Entgelte im Auftrag Dritter, BG-35 Skonto). Übergangsfristen sind noch offen. |

Aktuelle technische Komponenten (Bundle „3.0.2 Summer 2026 Bugfix“, 31.08.2026):

| Komponente | Version |
|---|---|
| KoSIT Validator | **v1.6.3** (20.08.2026), `validator-1.6.3-standalone.jar`, Java ≥ 11 |
| validator-configuration-xrechnung | **v2026-08-31**, `xrechnung-3.0.2-validator-configuration-2026-08-31.zip` |
| xrechnung-schematron | **v2.6.0** (2026-08-31), enthält Peppol BIS Billing 3.0.21 |
| xrechnung-visualization | **v2026-10-13** |
| xrechnung-testsuite | **v2026-08-31** |

**Architekturhinweis:** Die XRechnung-Version muss konfigurierbar sein (CustomizationID, Regeln). Auf 4.0 ist umzustellen, sobald das finale Bundle mit Übergangsfrist erschienen ist.

### Kennungen

| BT | Feld | Wert |
|---|---|---|
| **BT-24** Specification identifier | UBL `cbc:CustomizationID`, CII `ram:GuidelineSpecifiedDocumentContextParameter/ram:ID` | `urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0` |
| BT-24 (Extension) | dito | `urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0#conformant#urn:xeinkauf.de:kosit:extension:xrechnung_3.0` (nur bei Nutzung der Extension-Elemente, z. B. Sub-Invoice-Lines) |
| **BT-23** Business process type | UBL `cbc:ProfileID`, CII `ram:BusinessProcessSpecifiedDocumentContextParameter/ram:ID` | in XRechnung optional. Empfehlung: `urn:fdc:peppol.eu:2017:poacc:billing:01:1.0` (so in der KoSIT-Testsuite und Pflicht bei Versand über Peppol) |

Die CustomizationID enthält nur **Major.Minor** (`_3.0`), die Patch-Version also nicht. Das ergibt sich aus `common.sch`: `XR-MAJOR-MINOR-VERSION = '3.0'`. Weicht der Wert ab, warnt BR-DE-21. Der Validator findet dann zusätzlich kein passendes Szenario und weist die Datei ab.

### Leitweg-ID (BT-10 „Buyer reference“)

- **BT-10 ist in XRechnung immer Pflicht** (BR-DE-15, fatal), auch im B2B-Fall.
- **B2G:** Hier gehört die **Leitweg-ID** des öffentlichen Auftraggebers hinein, die dieser vorgibt. Aufbau: `Grobadressierung-Feinadressierung-Prüfziffer`:
  - Grobadressierung: 2–12 Ziffern (Bund/Land/Kommune), z. B. `991` = Bund, `04011000` = Bremen.
  - Feinadressierung: optional, bis 30 alphanumerische Zeichen.
  - Prüfziffer: 2 Ziffern nach **ISO 7064 Modulo 97-10** über die verketteten Teile ohne Bindestriche. Rechnung: `98 - (int(body + "00") % 97)`. Gegen `04011000-12345-03` geprüft. Wie Buchstaben in der Feinadressierung umgerechnet werden (A=10 … Z=35), ist (unverifiziert).
- **B2B:** Hier genügt ein mit dem Kunden vereinbarter Wert, z. B. Bestellnummer oder Kundennummer. Ein Platzhalter wie `n/a` ist gängige Praxis, aber (unverifiziert) als offizielle Empfehlung. **Das Feld darf nicht leer bleiben.**
- Die Leitweg-ID wird im B2G-Fall typischerweise auch als Käufer-Endpunkt **BT-49** mit `schemeID="0204"` angegeben.

---

## 2. Syntaxen

| | UBL 2.1 | UN/CEFACT CII D16B |
|---|---|---|
| Root (Rechnung) | `Invoice` in `urn:oasis:names:specification:ubl:schema:xsd:Invoice-2` | `rsm:CrossIndustryInvoice` |
| Root (Gutschrift) | `CreditNote` in `urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2` | dasselbe Root, Typcode 381 |
| Weitere Namespaces | `cac` = `urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2`; `cbc` = `urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2` | `rsm` = `urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100`; `ram` = `urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100`; `udt` = `urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100`; `qdt` = `urn:un:unece:uncefact:data:standard:QualifiedDataType:100` |
| Datumsformat | `YYYY-MM-DD` (BR-TMP-6, derzeit Warnung, wird später fatal) | `<udt:DateTimeString format="102">YYYYMMDD</udt:DateTimeString>` (BR-TMP-7, derzeit Warnung) |
| Elementreihenfolge | streng nach XSD-Sequenz | streng nach XSD-Sequenz |

**Empfehlung für openbilling: CII D16B als primäre Syntax.**
1. ZUGFeRD/Factur-X (Hybrid-PDF, siehe 8) erlaubt **nur CII**. Dasselbe CII-XML kann deshalb als reine XRechnung versendet und in das PDF/A-3 eingebettet werden.
2. Die Behördenportale (ZRE, OZG-RE) und der KoSIT-Validator akzeptieren beide Syntaxen.
3. UBL (Invoice und CreditNote) ist optional als Exportformat sinnvoll, vor allem für Peppol, wo UBL üblich ist. Das Datenmodell sollte deshalb intern nach **BT-Nummern** aufgebaut sein und die Syntax nur ein Serializer.

---

## 3. Pflicht-Business-Terms (Auswahl für ein Rechnungstool)

Pfadkürzel: UBL relativ zu `/Invoice`. Für CII gilt:
`AGR` = `/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:ApplicableHeaderTradeAgreement`,
`DEL` = `…/ram:ApplicableHeaderTradeDelivery`, `SET` = `…/ram:ApplicableHeaderTradeSettlement`, `DOC` = `/rsm:CrossIndustryInvoice/rsm:ExchangedDocument`.
Spalte „P“: **M** = Pflicht in EN 16931, **DE** = zusätzlich Pflicht durch XRechnung, **C** = bedingt.

| BT/BG | Name | P | UBL | CII | Hinweise |
|---|---|---|---|---|---|
| BT-1 | Invoice number | M | `cbc:ID` | `DOC/ram:ID` | eindeutig (§ 14 UStG) |
| BT-2 | Issue date | M | `cbc:IssueDate` | `DOC/ram:IssueDateTime/udt:DateTimeString` | |
| BT-3 | Invoice type code | M | `cbc:InvoiceTypeCode` (CN: `cbc:CreditNoteTypeCode`) | `DOC/ram:TypeCode` | siehe 4.2 |
| BT-5 | Currency | M | `cbc:DocumentCurrencyCode` | `SET/ram:InvoiceCurrencyCode` | ISO 4217, z. B. `EUR`. Alle Beträge tragen `currencyID` (UBL). |
| BT-9 | Due date | C | `cbc:DueDate` (CN: `cac:PaymentMeans/cbc:PaymentDueDate`) | `SET/ram:SpecifiedTradePaymentTerms/ram:DueDateDateTime/udt:DateTimeString` | BT-9 oder BT-20 ist Pflicht, wenn BT-115 > 0 (BR-CO-25) |
| BT-10 | Buyer reference / Leitweg-ID | **DE** | `cbc:BuyerReference` | `AGR/ram:BuyerReference` | BR-DE-15 |
| BT-20 | Payment terms | C | `cac:PaymentTerms/cbc:Note` | `SET/ram:SpecifiedTradePaymentTerms/ram:Description` | Skonto-Syntax: `#SKONTO#TAGE=14#PROZENT=2.00#` (BR-DE-18) |
| BT-24 | Specification identifier | M | `cbc:CustomizationID` | `/rsm:CrossIndustryInvoice/rsm:ExchangedDocumentContext/ram:GuidelineSpecifiedDocumentContextParameter/ram:ID` | siehe 1 |
| BT-27 | Seller name | M | `cac:AccountingSupplierParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName` | `AGR/ram:SellerTradeParty/ram:Name` | |
| BT-29 / BT-30 | Seller identifier / legal registration ID | C | `…/cac:Party/cac:PartyIdentification/cbc:ID` / `…/cac:PartyLegalEntity/cbc:CompanyID` | `AGR/ram:SellerTradeParty/ram:ID` / `…/ram:SpecifiedLegalOrganization/ram:ID` | BT-29, BT-30 oder BT-31 Pflicht (BR-CO-26), siehe 6.1 |
| BT-31 | Seller VAT ID | C | `…/cac:Party/cac:PartyTaxScheme[cac:TaxScheme/cbc:ID='VAT']/cbc:CompanyID` | `AGR/ram:SellerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']` | BT-31, BT-32 oder BT-63 ist Pflicht bei Kategorie S/Z/E/AE/K/G/L/M (BR-DE-16) |
| BT-32 | Seller tax registration (Steuernummer) | C | `…/cac:PartyTaxScheme[cac:TaxScheme/cbc:ID!='VAT']/cbc:CompanyID` (TaxScheme z. B. `FC`) | `…/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='FC']` | für Kleinunternehmer ohne USt-IdNr. |
| **BT-34** | Seller electronic address | **DE** | `…/cac:Party/cbc:EndpointID/@schemeID` | `AGR/ram:SellerTradeParty/ram:URIUniversalCommunication/ram:URIID/@schemeID` | Pflicht über PEPPOL-EN16931-R020 (in XRechnung 3.0 übernommen). Meist `schemeID="EM"` + E-Mail. |
| BG-5 | Seller postal address | M | `…/cac:Party/cac:PostalAddress` | `AGR/ram:SellerTradeParty/ram:PostalTradeAddress` | Pflicht in DE: **BT-37 Stadt** (BR-DE-3), **BT-38 PLZ** (BR-DE-4), BT-40 Land (M). UBL: `cbc:StreetName`, `cbc:CityName`, `cbc:PostalZone`, `cac:Country/cbc:IdentificationCode`. CII: `ram:PostcodeCode`, `ram:LineOne`, `ram:CityName`, `ram:CountryID`. |
| **BG-6** | Seller contact | **DE** | `…/cac:Party/cac:Contact` | `AGR/ram:SellerTradeParty/ram:DefinedTradeContact` | BR-DE-2. Alle drei Felder Pflicht: **BT-41 Name** (UBL `cbc:Name`, CII `ram:PersonName`), **BT-42 Telefon** (`cbc:Telephone` / `ram:TelephoneUniversalCommunication/ram:CompleteNumber`, mind. 3 Ziffern), **BT-43 E-Mail** (`cbc:ElectronicMail` / `ram:EmailURIUniversalCommunication/ram:URIID`). Siehe BR-DE-5/6/7/27/28. |
| BT-44 | Buyer name | M | `cac:AccountingCustomerParty/cac:Party/cac:PartyLegalEntity/cbc:RegistrationName` | `AGR/ram:BuyerTradeParty/ram:Name` | |
| BT-48 | Buyer VAT ID | C | `…/cac:PartyTaxScheme/cbc:CompanyID` | `AGR/ram:BuyerTradeParty/ram:SpecifiedTaxRegistration/ram:ID[@schemeID='VA']` | Pflicht bei AE, K (siehe 6) |
| **BT-49** | Buyer electronic address | **DE** | `cac:AccountingCustomerParty/cac:Party/cbc:EndpointID/@schemeID` | `AGR/ram:BuyerTradeParty/ram:URIUniversalCommunication/ram:URIID/@schemeID` | Pflicht über PEPPOL-EN16931-R010. B2G: `0204` + Leitweg-ID. B2B: meist `EM` + E-Mail. |
| BG-8 | Buyer postal address | M | `…/cac:PostalAddress` | `…/ram:PostalTradeAddress` | Pflicht in DE: **BT-52 Stadt** (BR-DE-8), **BT-53 PLZ** (BR-DE-9), BT-55 Land |
| BT-72 / BG-14 | Delivery date / Invoicing period | (DE-Info) | `cac:Delivery/cbc:ActualDeliveryDate` bzw. `cac:InvoicePeriod/cbc:StartDate`, `cbc:EndDate` | `DEL/ram:ActualDeliverySupplyChainEvent/ram:OccurrenceDateTime/udt:DateTimeString` bzw. `SET/ram:BillingSpecifiedPeriod/ram:StartDateTime`, `ram:EndDateTime` | BR-DE-TMP-32 („information“). Nach § 14 Abs. 4 Nr. 6 UStG trotzdem immer angeben. |
| **BG-16** | Payment instructions | **DE** | `cac:PaymentMeans` | `SET/ram:SpecifiedTradeSettlementPaymentMeans` | BR-DE-1 (fatal) |
| BT-81 | Payment means code | M | `cac:PaymentMeans/cbc:PaymentMeansCode` | `…/ram:TypeCode` | siehe 4.3 |
| BT-83 | Remittance information (Verwendungszweck) | | `cac:PaymentMeans/cbc:PaymentID` | `SET/ram:PaymentReference` | |
| BT-84 | IBAN (BG-17 Credit transfer) | C | `cac:PaymentMeans/cac:PayeeFinancialAccount/cbc:ID` | `…/ram:PayeePartyCreditorFinancialAccount/ram:IBANID` | Pflicht bei 30/58 (BR-DE-23-a). Bei 58 IBAN-Prüfziffer prüfen (BR-DE-19). |
| BT-85 / BT-86 | Account name / BIC | | `…/cbc:Name` / `…/cac:FinancialInstitutionBranch/cbc:ID` | `…/ram:AccountName` / `…/ram:PayeeSpecifiedCreditorFinancialInstitution/ram:BICID` | |
| BT-82 | Payment means text | | `cac:PaymentMeans/cbc:PaymentMeansCode/@name` | `…/ram:SpecifiedTradeSettlementPaymentMeans/ram:Information` | z. B. „PayPal“ zu Code 68. CII geprüft, UBL (unverifiziert) |
| BG-18 | Payment card | C | `cac:PaymentMeans/cac:CardAccount`: BT-87 `cbc:PrimaryAccountNumberID`, BT-88 `cbc:HolderName` | `…/ram:SpecifiedTradeSettlementPaymentMeans/ram:ApplicableTradeSettlementFinancialCard`: BT-87 `ram:ID`, BT-88 `ram:CardholderName` | Code 48/54/55: BR-DE-24-a/b. BR-51: nie die ganze Kartennummer, höchstens die ersten 6 und letzten 4 Ziffern. CII geprüft (KoSIT 1.6.3), UBL (unverifiziert) |
| BG-19 | Direct debit | C | `cac:PaymentMeans/cac:PaymentMandate`: BT-89 `cbc:ID`, BT-91 `cac:PayerFinancialAccount/cbc:ID`. BT-90 (Gläubiger-ID) liegt in `cac:AccountingSupplierParty/cac:Party/cac:PartyIdentification/cbc:ID[@schemeID='SEPA']`. | BT-89 `SET/ram:SpecifiedTradePaymentTerms/ram:DirectDebitMandateID`, BT-90 `SET/ram:CreditorReferenceID`, BT-91 `…/ram:PayerPartyDebtorFinancialAccount/ram:IBANID` | Code 59: BR-DE-25-a/b, -30, -31, -20. CII geprüft (KoSIT 1.6.3): BT-89 steht in `SpecifiedTradePaymentTerms`, das Element ist dann auch ohne BT-9 und BT-20 nötig |
| **BG-22** | Document totals | M | `cac:LegalMonetaryTotal` | `SET/ram:SpecifiedTradeSettlementHeaderMonetarySummation` | Feldzuordnung: BT-106 `cbc:LineExtensionAmount` / `ram:LineTotalAmount`; BT-107 `cbc:AllowanceTotalAmount` / `ram:AllowanceTotalAmount`; BT-108 `cbc:ChargeTotalAmount` / `ram:ChargeTotalAmount`; BT-109 `cbc:TaxExclusiveAmount` / `ram:TaxBasisTotalAmount`; BT-110 `cac:TaxTotal/cbc:TaxAmount` / `ram:TaxTotalAmount[@currencyID]`; BT-112 `cbc:TaxInclusiveAmount` / `ram:GrandTotalAmount`; BT-113 `cbc:PrepaidAmount` / `ram:TotalPrepaidAmount`; BT-114 `cbc:PayableRoundingAmount` / `ram:RoundingAmount`; BT-115 `cbc:PayableAmount` / `ram:DuePayableAmount` |
| **BG-23** | VAT breakdown (je Kategorie und Satz) | M | `cac:TaxTotal/cac:TaxSubtotal` | `SET/ram:ApplicableTradeTax` | BT-116 `cbc:TaxableAmount` / `ram:BasisAmount`; BT-117 `cbc:TaxAmount` / `ram:CalculatedAmount`; BT-118 `cac:TaxCategory/cbc:ID` / `ram:CategoryCode`; **BT-119 `cbc:Percent` / `ram:RateApplicablePercent` (in DE immer Pflicht, auch 0: BR-DE-14)**; BT-120/121 `cbc:TaxExemptionReason`/`…Code` bzw. `ram:ExemptionReason`/`…Code`; CII zusätzlich `ram:TypeCode`=`VAT` |
| **BG-25** | Invoice line (≥ 1) | M | `cac:InvoiceLine` (CN: `cac:CreditNoteLine`) | `/rsm:CrossIndustryInvoice/rsm:SupplyChainTradeTransaction/ram:IncludedSupplyChainTradeLineItem` | |
| BT-126 | Line ID | M | `cbc:ID` | `ram:AssociatedDocumentLineDocument/ram:LineID` | |
| BT-129/130 | Quantity / Unit | M | `cbc:InvoicedQuantity/@unitCode` (CN: `cbc:CreditedQuantity`) | `ram:SpecifiedLineTradeDelivery/ram:BilledQuantity/@unitCode` | Rec 20 / 21 |
| BT-131 | Line net amount | M | `cbc:LineExtensionAmount` | `ram:SpecifiedLineTradeSettlement/ram:SpecifiedTradeSettlementLineMonetarySummation/ram:LineTotalAmount` | |
| BT-146 / BT-149 | Net price / Base quantity | M / opt. | `cac:Price/cbc:PriceAmount` / `cac:Price/cbc:BaseQuantity` | `ram:SpecifiedLineTradeAgreement/ram:NetPriceProductTradePrice/ram:ChargeAmount` / `…/ram:BasisQuantity` | Preis ≥ 0 (BR-27) |
| BT-151/152 | Line VAT category / rate | M | `cac:Item/cac:ClassifiedTaxCategory/cbc:ID` / `cbc:Percent` | `ram:SpecifiedLineTradeSettlement/ram:ApplicableTradeTax/ram:CategoryCode` / `ram:RateApplicablePercent` | |
| BT-153 | Item name | M | `cac:Item/cbc:Name` | `ram:SpecifiedTradeProduct/ram:Name` | |

---

## 4. Codelisten (Auszug)

### 4.1 VAT category code (UNCL 5305, BT-95/102/118/151)

| Code | Bedeutung | Satz BT-119 | Pflicht-Begleitangaben |
|---|---|---|---|
| `S` | Standard rate (19 %, 7 %) | > 0 | BT-31/32/63 |
| `Z` | Zero rated goods | 0 | |
| `E` | Exempt from tax (steuerfrei, u. a. § 4 UStG und § 19 Kleinunternehmer) | 0 | BT-120 oder BT-121 (BR-E-10) |
| `AE` | VAT Reverse Charge (§ 13b UStG) | 0 | BT-120/121, BT-31 **und** BT-48 (BR-AE-*) |
| `K` | Innergemeinschaftliche Lieferung/Leistung | 0 | BT-120/121, USt-IdNr. Verkäufer und Käufer, Lieferdatum und Lieferland |
| `G` | Free export item, tax not charged (Ausfuhr) | 0 | BT-120/121 |
| `O` | Services outside scope of tax (nicht steuerbar) | **kein** Satz (BR-O-*) | BT-120/121. Weder BT-31 noch BT-48 angeben. Nicht in der BR-DE-16-Liste. |
| `L` | IGIC (Kanaren) | | nicht relevant |
| `M` | IPSI (Ceuta/Melilla) | | nicht relevant |

VATEX-Codes für BT-121, z. B. `VATEX-EU-AE`, `VATEX-EU-IC`, `VATEX-EU-G`, `VATEX-EU-O`.

### 4.2 Invoice type code (UNTDID 1001, BT-3)

Nach BR-DE-17 (Warnung) sind nur diese Codes zulässig:

| Code | Bedeutung |
|---|---|
| `380` | Commercial invoice (Standardrechnung) |
| `381` | Credit note (Gutschrift / Rechnungskorrektur mit Minus). In UBL als `CreditNote`-Dokument. |
| `384` | Corrected invoice: soll BG-3 (Vorgängerrechnung) enthalten (BR-DE-26) |
| `389` | Self-billed invoice (Gutschrift im umsatzsteuerlichen Sinn) |
| `326` | Partial invoice (Abschlags-/Teilrechnung) |
| `875` / `876` / `877` | Partial construction / Partial final construction / Final construction invoice (Bau) |

### 4.3 Payment means (UNTDID 4461, BT-81)

| Code | Bedeutung | XRechnung-Regel |
|---|---|---|
| `58` | SEPA credit transfer | BG-17 mit IBAN Pflicht, keine BG-18/19 (BR-DE-23-a/b, -19) |
| `59` | SEPA direct debit | BG-19 Pflicht, BT-89 Mandat, BT-90 Gläubiger-ID, BT-91 IBAN des Zahlers (BR-DE-25-a/b, -30, -31, -20) |
| `30` | Credit transfer (nicht SEPA) | wie 58, ohne IBAN-Prüfziffernprüfung |
| `31` | Debit transfer | (unverifiziert) |
| `48` / `54` / `55` | Bank card / Credit card / Debit card | BG-18 Pflicht (BR-DE-24-a/b) |
| `10` | In cash | |
| `1` | Instrument not defined | |
| `57` | Standing agreement (Dauerauftrag) | |
| `ZZZ` | Mutually defined | |

### 4.4 Einheiten (UN/ECE Rec 20 + Rec 21, BT-130)

| Code | Einheit | | Code | Einheit |
|---|---|---|---|---|
| `C62` | Stück („one“, generisch) | | `H87` | Stück (piece) |
| `HUR` | Stunde | | `MIN` | Minute |
| `DAY` | Tag | | `WEE` | Woche |
| `MON` | Monat | | `ANN` | Jahr |
| `KGM` | Kilogramm | | `GRM` | Gramm |
| `MTR` | Meter | | `KMT` | Kilometer |
| `MTK` | Quadratmeter | | `MTQ` | Kubikmeter |
| `LTR` | Liter | | `KWH` | Kilowattstunde |
| `LS` | Pauschale (lump sum) | | `SET` | Set |
| `XPP` | Packstück (Rec 21, „X“-Präfix) | | `P1` | Prozent |

### 4.5 Electronic address scheme (CEF EAS, `@schemeID` von BT-34/BT-49)

| Code | Bedeutung |
|---|---|
| `EM` | E-Mail-Adresse (in DE B2B am gebräuchlichsten) |
| `0204` | **Leitweg-ID** (DE, B2G) |
| `9930` | Deutsche USt-IdNr. (DE VAT) |
| `0088` | GLN (GS1) |
| `0060` | DUNS |
| `0208` | Belgische Unternehmensnummer |
| `9925` | Belgische USt-IdNr. |

Die komplette Liste steht in `common.sch` (`CEF-EAS-CODES`). Die Extension erlaubt zusätzlich `XR01`–`XR03` (DiGA).

---

## 5. Rechenregeln (EN 16931 BR-CO-*, BR-DEC-*, BR-S-*)

**Rundung:** Alle Beträge der Kopf- und Steuerebene sowie die Zeilennettobeträge haben **maximal 2 Nachkommastellen** (BR-DEC-*). Preise (BT-146) und Mengen (BT-129) dürfen mehr haben. Intern mit `Decimal` rechnen, nie mit `float`, und kaufmännisch runden (`ROUND_HALF_UP`).

| Regel | Formel |
|---|---|
| Zeile | **BT-131 = round(BT-129 × BT-146 / BT-149, 2) − Σ Zeilenabschläge (BG-27, BT-136) + Σ Zeilenzuschläge (BG-28, BT-141)**; BT-149 hat Default 1 |
| Preis | BT-146 = BT-148 (Bruttopreis) − BT-147 (Preisrabatt) |
| BR-CO-10 | BT-106 = Σ BT-131 |
| BR-CO-11 / -12 | BT-107 = Σ BT-92 (Belegabschläge), BT-108 = Σ BT-99 (Belegzuschläge) |
| BR-CO-13 | BT-109 = BT-106 − BT-107 + BT-108 |
| BR-S-08 (analog Z/E/AE/K/G/O) | BT-116 je (Kategorie, Satz) = Σ BT-131 der Zeilen + Σ BT-99 − Σ BT-92 dieser Kategorie und dieses Satzes |
| BR-S-09 | BT-117 = round(BT-116 × BT-119 / 100, 2). **Die Steuer wird pro Kategorie und Satz berechnet, nicht pro Zeile.** |
| BR-E/AE/K/G/Z | BT-117 = 0 |
| BR-CO-14 | BT-110 = Σ BT-117 |
| BR-CO-15 | BT-112 = BT-109 + BT-110 |
| BR-CO-16 | BT-115 = BT-112 − BT-113 + BT-114 |
| BR-CO-25 | Wenn BT-115 > 0, ist BT-9 oder BT-20 Pflicht |
| BR-CO-26 | Mindestens eines von BT-29, BT-30 oder BT-31 (Verkäuferkennung) |
| BR-45 ff. | Für jede vorkommende (Kategorie, Satz)-Kombination genau eine BG-23 |

### 5.1 Abschläge, Zuschläge und Skonto in CII

Geprüft am 23.11.2026 mit KoSIT 1.6.3 und der Konfiguration 2026-08-31 (`CiiSchreiberTest`).

- **Element:** `ram:SpecifiedTradeAllowanceCharge` mit der Reihenfolge `ChargeIndicator/udt:Indicator` (`false` Abschlag, `true` Zuschlag), `CalculationPercent`, `BasisAmount`, `ActualAmount`, `ReasonCode`, `Reason`, `CategoryTradeTax`.
- **Position (BG-27/28):** in `SpecifiedLineTradeSettlement` nach `ApplicableTradeTax` (und `BillingSpecifiedPeriod`), vor `SpecifiedTradeSettlementLineMonetarySummation`. Ohne `CategoryTradeTax`. Grund (BT-139/144) oder Code (BT-140/145) ist Pflicht (BR-42/BR-44).
- **Beleg (BG-20/21):** in `ApplicableHeaderTradeSettlement` nach `ApplicableTradeTax` (BG-23) und `BillingSpecifiedPeriod`, vor `SpecifiedTradePaymentTerms`. `CategoryTradeTax` mit `TypeCode` VAT, `CategoryCode` (BT-95/102, BR-32/BR-37) und `RateApplicablePercent` (außer bei O). Grund oder Code ist Pflicht (BR-33/BR-38).
- **Summen:** In `SpecifiedTradeSettlementHeaderMonetarySummation` stehen `ChargeTotalAmount` (BT-108) und `AllowanceTotalAmount` (BT-107) in dieser Reihenfolge zwischen `LineTotalAmount` und `TaxBasisTotalAmount`.
- **Kategorie O** darf nicht mit anderen Kategorien in einer Rechnung stehen (BR-O-11 ff.); ein Test mit S und O zusammen ist daher kein gültiges Beispiel. S zusammen mit E geht.
- **Skonto (BR-DE-18, fatal):** Regex im Schematron (`XR-SKONTO-REGEX` in `XRechnung-CII-validation.xsl`): `#SKONTO#TAGE=([0-9]+#PROZENT=[0-9]+\.[0-9]{2})(#BASISBETRAG=-?[0-9]+\.[0-9]{2})?#`. Geprüft wird nur die erste `ram:Description` in `SpecifiedTradePaymentTerms`. Jede Zeile, die mit `#` beginnt, muss der Regex genügen, und **nach dem letzten `#…#` muss ein Zeilenumbruch folgen**. Der Prozentsatz braucht genau zwei Nachkommastellen (`2.00`, nicht `2`). Freitext darf davor stehen, aber keine Freitextzeile darf mit `#` beginnen. Die Tage zählen ab Rechnungsdatum (unverifiziert gegen den Spezifikationstext).

Bruttopreise wie im Einzelhandel sieht EN 16931 nicht vor. Das BMF bestätigt im Schreiben vom 15.10.2026, dass dabei Rundungsdifferenzen entstehen können. openbilling sollte deshalb Nettopreise speichern.

---

## 6. Steuerliche Sonderfälle

### 6.1 Kleinunternehmer (§ 19 UStG)

- Kleinunternehmer sind von der **Ausstellungspflicht** für E-Rechnungen befreit (§ 34a UStDV), müssen E-Rechnungen aber **empfangen** können. Eine XRechnung zu erzeugen ist trotzdem zulässig und bei B2G nötig.
- Abbildung: Kategorie **`E`** mit **BT-119 = 0**. BT-120 als Text, z. B. „Kein Ausweis von Umsatzsteuer, da Kleinunternehmer gemäß § 19 UStG“. Einen eigenen VATEX-Code für § 19 gibt es nicht (unverifiziert); BT-121 kann deshalb leer bleiben.
- BR-DE-16 verlangt dann **BT-31 (USt-IdNr.) oder BT-32 (Steuernummer, UBL TaxScheme `FC`, CII `schemeID="FC"`)**.
- **Achtung, BR-CO-26 (fatal):** Die Steuernummer BT-32 allein genügt **nicht**. Ohne USt-IdNr. (BT-31) braucht der Verkäufer zusätzlich eine **Verkäuferkennung BT-29** (CII `SellerTradeParty/ram:ID`, vor `ram:Name`) oder eine **Registernummer BT-30** (CII `SellerTradeParty/ram:SpecifiedLegalOrganization/ram:ID`). Geprüft am 23.11.2026 mit KoSIT 1.6.3: Nur mit Steuernummer wird die Datei abgewiesen, mit `ram:ID` = `KU-4711` ist sie ACCEPTABLE (`CiiSchreiberTest`). Welcher Wert für BT-29 bei Kleinunternehmern üblich ist (eigene Lieferantennummer beim Kunden, Steuernummer o. Ä.), ist (unverifiziert).
- BT-117 und BT-110 sind 0.00, BT-112 ist gleich BT-109.

### 6.2 Kleinbetragsrechnung (§ 33 UStDV, Gesamtbetrag ≤ 250 € brutto)

- Für sie gilt keine E-Rechnungspflicht; eine „sonstige Rechnung“ (Papier oder PDF) genügt. Pflichtangaben sind reduziert: Name und Anschrift des Leistenden, Datum, Menge und Art, Entgelt und Steuer in einer Summe sowie der Steuersatz.
- Wer trotzdem eine XRechnung erzeugt, muss alle XRechnung-Pflichtfelder füllen; das Schema kennt keine Vereinfachung. Käuferadresse, BG-23 usw. bleiben Pflicht.
- Fahrausweise (§ 34 UStDV) sind ebenfalls ausgenommen.

### 6.3 Reverse Charge (§ 13b UStG) und EU-Fälle

- **`AE`**: BT-119 = 0, BT-117 = 0. BT-120 lautet „Steuerschuldnerschaft des Leistungsempfängers“ (Pflichtvermerk nach § 14a Abs. 5 UStG), BT-121 `VATEX-EU-AE`. Die USt-IdNr. oder Steuernummer des Verkäufers und die **USt-IdNr. des Käufers (BT-48)** sind Pflicht. Wenn eine Rechnung AE enthält, darf es nur eine BG-23 mit AE geben (BR-AE-*).
- **`K`** (innergemeinschaftliche Lieferung): BT-121 `VATEX-EU-IC`, BT-31 und BT-48 Pflicht, dazu BT-72 oder BG-14 und das Lieferland (BT-80).
- **`G`** (Export Drittland): BT-121 `VATEX-EU-G`.

### 6.4 E-Rechnungspflicht B2B (Wachstumschancengesetz, § 14 UStG, § 27 Abs. 38 UStG)

| Ab | Regel |
|---|---|
| 01.01.2025 | Alle Unternehmer müssen E-Rechnungen (EN 16931: XRechnung, ZUGFeRD ≥ 2.0.1 außer MINIMUM und BASIC WL) **empfangen** können. Ein E-Mail-Postfach genügt. |
| 2025–2026 | Für die Ausstellung sind Papier oder (mit Zustimmung) PDF weiterhin erlaubt. |
| 01.01.2027 | Ausstellungspflicht für Unternehmer mit einem **Vorjahresumsatz über 800.000 €**. Die anderen dürfen bis Ende 2027 noch sonstige Rechnungen ausstellen. EDI-Formate sind bis 31.12.2027 zulässig. |
| 01.01.2028 | Ausstellungspflicht für alle inländischen B2B-Umsätze |

BMF-Schreiben:
- **15.10.2024** (III C 2 - S 7287-a/23/10001 :007): Grundsatzschreiben. Danach gehen beim Hybridformat die **XML-Daten vor**, wenn sie vom PDF abweichen.
- **15.10.2025** (BStBl I S. 1806): Ergänzungen und Präzisierungen.
- **15.10.2026**: drittes Schreiben. Es unterscheidet **Formatfehler** (dann keine E-Rechnung) von **Geschäftsregelfehlern** (bleibt eine E-Rechnung, ist aber ggf. inhaltlich fehlerhaft). Es empfiehlt das Mapping der Pflichtangaben auf BT/BG nach den BMF-Mappingtabellen. Für 2027 gilt eine Vereinfachung: Im strukturierten Teil reichen Summen je Steuersatz, wenn ein eingebetteter lesbarer Anhang die Einzelpositionen enthält. Korrekturen sind als E-Rechnung mit Korrektur-Typcode auszustellen.

Das Aktenzeichen des Schreibens von 2026 ist (unverifiziert). Die genauen Randziffern sollten im Volltext auf bundesfinanzministerium.de nachgelesen werden.

---

## 7. Validierung (KoSIT Validator)

```bash
# 1) Artefakte laden (Java >= 11 nötig)
curl -L -o validator-1.6.3-standalone.jar \
  https://repo1.maven.org/maven2/org/kosit/validator/1.6.3/validator-1.6.3-standalone.jar
curl -L -o cfg.zip https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/download/v2026-08-31/xrechnung-3.0.2-validator-configuration-2026-08-31.zip
unzip cfg.zip -d xrcfg
# 2) Prüfen (-h schreibt zusätzlich einen HTML-Report)
java -jar validator-1.6.3-standalone.jar -s xrcfg/scenarios.xml -r xrcfg -h rechnung.xml
# Ergebnis: rechnung-report.xml (+ rechnung-report.html)
```

Im Repo kapseln `scripts/setup-validator.ps1` und `scripts/validate.ps1` diese Schritte. Die Option `-o <ordner>` funktioniert (geprüft mit 1.6.3). Zu Windows-Stolperfallen siehe `lessons-learned.md`.

| Option | Bedeutung |
|---|---|
| `-s <scenarios.xml>` | Szenario-Konfiguration (mehrfach möglich, auch `NAME=pfad`) |
| `-r <repo>` | Verzeichnis mit den Ressourcen (XSD, XSL) |
| `-o <dir>` | Ausgabeverzeichnis (unverifiziert; in cli.md nicht aufgeführt, siehe `--help`) |
| `-h` | HTML aus dem Report extrahieren (**nicht** die Hilfe; die Hilfe ist `--help`) |
| `-p` | Report auf stdout ausgeben |
| `-D` | Daemon-Modus (HTTP-Server, POST der XML-Datei) |
| `-m` | Speicherverbrauch ausgeben |

**Exit-Codes:** `0` = alle Dateien akzeptiert; `>0` = Anzahl der abgewiesenen Dateien; `-1` = falsche Argumente; `-2` = Konfigurationsfehler.

**Ablauf:** Das Szenario wird über die Root-Namespace und `CustomizationID` ausgewählt. Passt keine, wird die Datei abgewiesen. Geprüft wird in drei Stufen: (1) XSD (UBL 2.1 oder CII D16B), (2) EN-16931-Schematron (`BR-*`, `BR-CO-*`, `BR-S-*`, …), (3) XRechnung-Schematron (`BR-DE-*`, `BR-DEX-*` für die Extension, `BR-TMP-*` als temporäre Regeln, übernommene `PEPPOL-EN16931-R*`).

**Report lesen:** Der XML-Report im KoSIT-Report-Namespace enthält eine Empfehlung `accept`/`reject` und je Fehler `rep:message` mit `@id` (Regel-ID), `@level` (`error` bzw. `warning` bzw. `information`) und `@location` (XPath). Die Flags kommen aus dem Schematron: `fatal` ergibt `error`, also ein **Reject**. `warning` und `information` führen nicht zum Reject. openbilling sollte nur bei `accept` und **0 Warnungen** exportieren und Warnungen dem Nutzer mit BT-Bezug anzeigen.

Wichtige XRechnung-Regeln:

| ID | Flag | Inhalt |
|---|---|---|
| BR-DE-1 | fatal | BG-16 Zahlungsanweisungen Pflicht |
| BR-DE-2, -5, -6, -7 | fatal | BG-6 mit BT-41, BT-42, BT-43 |
| BR-DE-3/-4, -8/-9 | fatal | Stadt und PLZ von Verkäufer bzw. Käufer |
| BR-DE-14 | fatal | BT-119 Steuersatz in jeder BG-23 |
| BR-DE-15 | fatal | BT-10 Buyer reference |
| BR-DE-16 | fatal | Steuernummer oder USt-IdNr. bei S/Z/E/AE/K/G/L/M |
| BR-DE-17 | warning | zulässige Typcodes (4.2) |
| BR-DE-18 | fatal | Skonto-Syntax in BT-20 |
| BR-DE-19 / -20 | warning | gültige IBAN bei 58 bzw. 59 |
| BR-DE-21 | warning | CustomizationID ist die XRechnung-Kennung |
| BR-DE-22 | fatal | eindeutige `filename` der eingebetteten Anhänge |
| BR-DE-23/24/25-a/b | fatal | Konsistenz von Zahlungsart und BG-17/18/19 |
| BR-DE-26 | warning | 384 verlangt BG-3 |
| BR-DE-27 / -28 | warning | Format von Telefon und E-Mail |
| BR-DE-TMP-32 | information | Liefer- oder Leistungsdatum fehlt |
| BR-TMP-2 | **fatal** (seit 2.6.0) | BT-124 ist eine absolute URL |
| BR-TMP-6 / -7 | warning (wird fatal) | Datumsformat UBL bzw. CII |
| PEPPOL-EN16931-R010 / R020 | fatal | Käufer- bzw. Verkäufer-Endpoint (BT-49 bzw. BT-34) |

Zulässige MIME-Typen für eingebettete Anhänge (BT-125): `application/pdf`, `image/png`, `image/jpeg`, `text/csv`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/vnd.oasis.opendocument.spreadsheet`, `application/xml` (vollständige Liste laut ubl.sch v2.6.0). Eine **ODS-Datei kann also als Anhang in der XRechnung selbst mitgeschickt werden.**

Testdaten: `xrechnung-testsuite` (Mirror auf GitHub, Original auf projekte.kosit.org), z. B. `src/test/business-cases/standard/01.01a-INVOICE_ubl.xml`. Eine Visualisierung (XRechnung nach HTML/PDF per XSLT) bietet `xrechnung-visualization`.

---

## 8. PDF mit eingebettetem XML (Hybrid)

- **Eine XRechnung ist reines XML.** Ein PDF ist dabei nur eine Sichtkopie. Für B2G (ZRE, OZG-RE) ist **das XML rechtlich maßgeblich**, und auch im B2B gilt laut BMF bei Abweichungen das XML.
- Das Hybridformat ist **ZUGFeRD 2.x = Factur-X 1.x**: ein PDF/A-3 mit eingebettetem **CII-XML**. Aktuell ist **ZUGFeRD 2.5.2 / Factur-X 1.09.2**, wirksam ab 01.09.2026. Davor galt 2.4 bzw. 1.08 (ab 15.01.2026).
  - Profile: MINIMUM, BASIC WL, BASIC, **EN 16931 (COMFORT)**, EXTENDED sowie das Referenzprofil **XRECHNUNG**. MINIMUM und BASIC WL gelten in DE **nicht** als E-Rechnung.
  - Für openbilling ist das Profil **XRECHNUNG** zu verwenden: Das eingebettete CII-XML trägt die XRechnung-CustomizationID (BT-24 wie in Abschnitt 1) und erfüllt alle BR-DE-Regeln. Behördenportale nehmen es an. Für reines B2B genügt auch **EN 16931** mit der GuidelineID `urn:cen.eu:en16931:2017`.
- **Technische Anforderungen:**

| Punkt | Wert |
|---|---|
| PDF-Standard | **PDF/A-3** (a, b oder u; in der Praxis meist PDF/A-3b) |
| Dateiname des Anhangs | `factur-x.xml`; **beim Profil XRECHNUNG `xrechnung.xml`**. So erzeugt es Mustang 2.26.0 mit Profil `XRECHNUNG`, und Mustangs Validator akzeptiert es (geprüft 23.11.2026, `PdfExportTest`). Gegen den Text der FeRD-Spezifikation selbst noch nicht gelesen. |
| Einbettung | `/EmbeddedFiles` und **`/AF`-Array im Catalog** mit Filespec `/AFRelationship` (von Mustang so erzeugt, geprüft) |
| AFRelationship | `/Alternative` (DE-Empfehlung für EN 16931, EXTENDED und XRECHNUNG; Mustang setzt beim Profil XRECHNUNG `Alternative`, geprüft), `/Data` (MINIMUM und BASIC WL sowie französische Vorgabe), `/Source`. Die Zuordnung für die anderen Profile ist (unverifiziert). |
| MIME-Typ | `text/xml` (Subtype `/text#2Fxml`, von Mustang so erzeugt, geprüft) |
| XMP-Metadaten | PDF/A-Extension-Schema mit Namespace `urn:factur-x:pdfa:CrossIndustryDocument:invoice:1p0#` (Präfix `fx`). Felder: `fx:DocumentType=INVOICE`, `fx:DocumentFileName=xrechnung.xml` bzw. `factur-x.xml`, `fx:Version=1.0`, `fx:ConformanceLevel=XRECHNUNG` bzw. `EN 16931`. Die ConformanceLevel muss zum XML passen. Für XRECHNUNG genau so von Mustang 2.26.0 erzeugt und von veraPDF (PDF/A-3u) akzeptiert. |
| Anzahl | genau **ein** Rechnungs-XML. Weitere Anhänge sind erlaubt. |

### Werkzeuge

| Zweck | Werkzeug | Hinweis |
|---|---|---|
| ODS → PDF/A | **LibreOffice headless**: `soffice --headless --convert-to 'pdf:calc_pdf_Export:{"SelectPdfVersion":{"type":"long","value":"3"}}' --outdir out rechnung.ods` | `SelectPdfVersion`: 1 = PDF/A-1b, 2 = PDF/A-2b, 3 = PDF/A-3b. JSON-Filteroptionen gibt es seit LO 7.4. Werte und Versionsgrenze sind (unverifiziert). Fonts müssen eingebettet sein. |
| XML einbetten (Python) | **factur-x** (Akretion, PyPI `factur-x`, v7.10 vom 19.11.2026): `facturx.generate_from_file(pdf, xml, output_pdf_file=..., afrelationship='alternative', attachments={...})`; CLI `facturx-pdfgen`, `facturx-xmlcheck`, `facturx-pdfextractxml` | Das Ergebnis ist nur dann PDF/A-3-konform, wenn das Eingabe-PDF schon PDF/A ist. Den eingebetteten Dateinamen und die XMP-ConformanceLevel für XRECHNUNG vorher prüfen (unverifiziert). |
| CII erzeugen (Python) | **drafthorse** (pretix): Datenmodell nach ZUGFeRD, `drafthorse.pdf.attach_xml()` | Profilunterstützung XRECHNUNG (unverifiziert) |
| Low-Level-PDF (Python) | **pikepdf** (qpdf): `/AF`, `/EmbeddedFiles`, XMP via `pdf.open_metadata()` | als Fallback oder für eine eigene Implementierung |
| Java | **Mustangproject** 2.26.0 (25.08.2026, `org.mustangproject:library`): ZUGFeRD 2.5 und CII-XRechnung 3.0.2, PDF/A-1→A-3, Mustang-CLI `--action combine` / `--action validate` | **In openbilling verwendet.** Geprüfter Ablauf (23.11.2026): Die Sichtkopie erzeugt openbilling selbst als XSL-FO und rendert sie mit FOP im Modus `pdf-a-mode=PDF/A-3b` (`PdfExport.sicht`, bis 23.11.2026 über `ZUGFeRDVisualizer.toPDF`); sie hat noch kein `/AF`. Danach `new ZUGFeRDExporterFromPDFA().load(pdf).setProfile(Profiles.getByName("XRECHNUNG")).setXML(bytes)` und `export(ziel)`. Das Ergebnis ist laut veraPDF (über `org.mustangproject:validator`) **PDF/A-3u-konform**, die eingebettete XML ist beim KoSIT-Validator ACCEPTABLE. Siehe `src/test/java/openbilling/pdf/PdfExportTest.java`. CLI-Flags weiter (unverifiziert). |
| PDF/A + XML prüfen (Java) | `org.mustangproject:validator` 2.26.0: `new ZUGFeRDValidator().validate(pfad)` liefert einen XML-Bericht, `wasCompletelyValid()` das Ergebnis. Enthält veraPDF und die XRechnung-Schematron. | nur als Test-Abhängigkeit eingebunden |
| PDF/A prüfen | veraPDF (`verapdf --flavour 3b datei.pdf`) | (unverifiziert, Flag) |

**Empfohlene Pipeline:** Zuerst das ODS anlegen. Dann (1) das Datenmodell nach BT-Nummern aus dem ODS lesen und (2) CII-XML erzeugen. (3) Das XML mit dem KoSIT-Validator prüfen (nur `accept`). (4) Das ODS mit LibreOffice in PDF/A-3b umwandeln. (5) Das XML mit factur-x oder pikepdf als `xrechnung.xml` einbetten (AFRelationship `Alternative`, XMP ConformanceLevel `XRECHNUNG`). (6) Optional mit veraPDF und dem Mustang-Validator gegenprüfen. **Zusätzlich das XML separat ausgeben**, denn Behördenportale verlangen meist die reine XML-Datei.

---

## 9. Minimalbeispiele (B2G, 1 Position, 19 % USt)

Rechnung: 10 h × 85,00 € = 850,00 € netto, 19 % USt = 161,50 €, brutto 1.011,50 €. Die Beispiele sind von Hand nach den XRechnung-Regeln und der Testsuite-Datei `01.01a` erstellt, Am 23.11.2026 mit KoSIT-Validator 1.6.3 und der Konfiguration XRechnung 3.0.2 (2026-08-31) geprüft: beide **ACCEPTABLE**. Sie liegen als `tests/fixtures/minimal-ubl.xml` und `tests/fixtures/minimal-cii.xml` im Repo. IBAN (`DE02120300000000202051`) und Leitweg-ID (`04011000-12345-03`) sind prüfziffernkorrekt.

### 9.1 UBL 2.1

```xml
<?xml version="1.0" encoding="UTF-8"?>
<ubl:Invoice xmlns:ubl="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
  xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
  xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
  <cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0</cbc:CustomizationID>
  <cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>
  <cbc:ID>RE-2026-0001</cbc:ID>
  <cbc:IssueDate>2026-11-23</cbc:IssueDate>
  <cbc:DueDate>2026-12-07</cbc:DueDate>
  <cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>
  <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
  <cbc:BuyerReference>04011000-12345-03</cbc:BuyerReference>
  <cac:AccountingSupplierParty><cac:Party>
    <cbc:EndpointID schemeID="EM">rechnung@beispiel-gmbh.de</cbc:EndpointID>
    <cac:PostalAddress>
      <cbc:StreetName>Musterstraße 1</cbc:StreetName>
      <cbc:CityName>Berlin</cbc:CityName>
      <cbc:PostalZone>10115</cbc:PostalZone>
      <cac:Country><cbc:IdentificationCode>DE</cbc:IdentificationCode></cac:Country>
    </cac:PostalAddress>
    <cac:PartyTaxScheme>
      <cbc:CompanyID>DE123456789</cbc:CompanyID>
      <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
    </cac:PartyTaxScheme>
    <cac:PartyLegalEntity><cbc:RegistrationName>Beispiel GmbH</cbc:RegistrationName></cac:PartyLegalEntity>
    <cac:Contact>
      <cbc:Name>Max Muster</cbc:Name>
      <cbc:Telephone>+49 30 1234567</cbc:Telephone>
      <cbc:ElectronicMail>max.muster@beispiel-gmbh.de</cbc:ElectronicMail>
    </cac:Contact>
  </cac:Party></cac:AccountingSupplierParty>
  <cac:AccountingCustomerParty><cac:Party>
    <cbc:EndpointID schemeID="0204">04011000-12345-03</cbc:EndpointID>
    <cac:PostalAddress>
      <cbc:StreetName>Amtsweg 5</cbc:StreetName>
      <cbc:CityName>Bremen</cbc:CityName>
      <cbc:PostalZone>28195</cbc:PostalZone>
      <cac:Country><cbc:IdentificationCode>DE</cbc:IdentificationCode></cac:Country>
    </cac:PostalAddress>
    <cac:PartyLegalEntity><cbc:RegistrationName>Freie Hansestadt Bremen, Senator für Finanzen</cbc:RegistrationName></cac:PartyLegalEntity>
  </cac:Party></cac:AccountingCustomerParty>
  <cac:Delivery><cbc:ActualDeliveryDate>2026-11-20</cbc:ActualDeliveryDate></cac:Delivery>
  <cac:PaymentMeans>
    <cbc:PaymentMeansCode>58</cbc:PaymentMeansCode>
    <cbc:PaymentID>RE-2026-0001</cbc:PaymentID>
    <cac:PayeeFinancialAccount>
      <cbc:ID>DE02120300000000202051</cbc:ID>
      <cbc:Name>Beispiel GmbH</cbc:Name>
    </cac:PayeeFinancialAccount>
  </cac:PaymentMeans>
  <cac:PaymentTerms><cbc:Note>Zahlbar innerhalb von 14 Tagen ohne Abzug.</cbc:Note></cac:PaymentTerms>
  <cac:TaxTotal>
    <cbc:TaxAmount currencyID="EUR">161.50</cbc:TaxAmount>
    <cac:TaxSubtotal>
      <cbc:TaxableAmount currencyID="EUR">850.00</cbc:TaxableAmount>
      <cbc:TaxAmount currencyID="EUR">161.50</cbc:TaxAmount>
      <cac:TaxCategory>
        <cbc:ID>S</cbc:ID><cbc:Percent>19</cbc:Percent>
        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
      </cac:TaxCategory>
    </cac:TaxSubtotal>
  </cac:TaxTotal>
  <cac:LegalMonetaryTotal>
    <cbc:LineExtensionAmount currencyID="EUR">850.00</cbc:LineExtensionAmount>
    <cbc:TaxExclusiveAmount currencyID="EUR">850.00</cbc:TaxExclusiveAmount>
    <cbc:TaxInclusiveAmount currencyID="EUR">1011.50</cbc:TaxInclusiveAmount>
    <cbc:PayableAmount currencyID="EUR">1011.50</cbc:PayableAmount>
  </cac:LegalMonetaryTotal>
  <cac:InvoiceLine>
    <cbc:ID>1</cbc:ID>
    <cbc:InvoicedQuantity unitCode="HUR">10</cbc:InvoicedQuantity>
    <cbc:LineExtensionAmount currencyID="EUR">850.00</cbc:LineExtensionAmount>
    <cac:Item>
      <cbc:Name>Beratungsleistung</cbc:Name>
      <cac:ClassifiedTaxCategory>
        <cbc:ID>S</cbc:ID><cbc:Percent>19</cbc:Percent>
        <cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme>
      </cac:ClassifiedTaxCategory>
    </cac:Item>
    <cac:Price><cbc:PriceAmount currencyID="EUR">85.00</cbc:PriceAmount></cac:Price>
  </cac:InvoiceLine>
</ubl:Invoice>
```

### 9.2 CII D16B (gleiche Rechnung, Skelett für ZUGFeRD-Profil XRECHNUNG)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rsm:CrossIndustryInvoice xmlns:rsm="urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100"
  xmlns:ram="urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100"
  xmlns:udt="urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100">
  <rsm:ExchangedDocumentContext>
    <ram:BusinessProcessSpecifiedDocumentContextParameter><ram:ID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</ram:ID></ram:BusinessProcessSpecifiedDocumentContextParameter>
    <ram:GuidelineSpecifiedDocumentContextParameter><ram:ID>urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0</ram:ID></ram:GuidelineSpecifiedDocumentContextParameter>
  </rsm:ExchangedDocumentContext>
  <rsm:ExchangedDocument>
    <ram:ID>RE-2026-0001</ram:ID><ram:TypeCode>380</ram:TypeCode>
    <ram:IssueDateTime><udt:DateTimeString format="102">20261123</udt:DateTimeString></ram:IssueDateTime>
  </rsm:ExchangedDocument>
  <rsm:SupplyChainTradeTransaction>
    <ram:IncludedSupplyChainTradeLineItem>
      <ram:AssociatedDocumentLineDocument><ram:LineID>1</ram:LineID></ram:AssociatedDocumentLineDocument>
      <ram:SpecifiedTradeProduct><ram:Name>Beratungsleistung</ram:Name></ram:SpecifiedTradeProduct>
      <ram:SpecifiedLineTradeAgreement><ram:NetPriceProductTradePrice><ram:ChargeAmount>85.00</ram:ChargeAmount></ram:NetPriceProductTradePrice></ram:SpecifiedLineTradeAgreement>
      <ram:SpecifiedLineTradeDelivery><ram:BilledQuantity unitCode="HUR">10</ram:BilledQuantity></ram:SpecifiedLineTradeDelivery>
      <ram:SpecifiedLineTradeSettlement>
        <ram:ApplicableTradeTax><ram:TypeCode>VAT</ram:TypeCode><ram:CategoryCode>S</ram:CategoryCode><ram:RateApplicablePercent>19</ram:RateApplicablePercent></ram:ApplicableTradeTax>
        <ram:SpecifiedTradeSettlementLineMonetarySummation><ram:LineTotalAmount>850.00</ram:LineTotalAmount></ram:SpecifiedTradeSettlementLineMonetarySummation>
      </ram:SpecifiedLineTradeSettlement>
    </ram:IncludedSupplyChainTradeLineItem>
    <ram:ApplicableHeaderTradeAgreement>
      <ram:BuyerReference>04011000-12345-03</ram:BuyerReference>
      <ram:SellerTradeParty>
        <ram:Name>Beispiel GmbH</ram:Name>
        <ram:DefinedTradeContact>
          <ram:PersonName>Max Muster</ram:PersonName>
          <ram:TelephoneUniversalCommunication><ram:CompleteNumber>+49 30 1234567</ram:CompleteNumber></ram:TelephoneUniversalCommunication>
          <ram:EmailURIUniversalCommunication><ram:URIID>max.muster@beispiel-gmbh.de</ram:URIID></ram:EmailURIUniversalCommunication>
        </ram:DefinedTradeContact>
        <ram:PostalTradeAddress><ram:PostcodeCode>10115</ram:PostcodeCode><ram:LineOne>Musterstraße 1</ram:LineOne><ram:CityName>Berlin</ram:CityName><ram:CountryID>DE</ram:CountryID></ram:PostalTradeAddress>
        <ram:URIUniversalCommunication><ram:URIID schemeID="EM">rechnung@beispiel-gmbh.de</ram:URIID></ram:URIUniversalCommunication>
        <ram:SpecifiedTaxRegistration><ram:ID schemeID="VA">DE123456789</ram:ID></ram:SpecifiedTaxRegistration>
      </ram:SellerTradeParty>
      <ram:BuyerTradeParty>
        <ram:Name>Freie Hansestadt Bremen, Senator für Finanzen</ram:Name>
        <ram:PostalTradeAddress><ram:PostcodeCode>28195</ram:PostcodeCode><ram:LineOne>Amtsweg 5</ram:LineOne><ram:CityName>Bremen</ram:CityName><ram:CountryID>DE</ram:CountryID></ram:PostalTradeAddress>
        <ram:URIUniversalCommunication><ram:URIID schemeID="0204">04011000-12345-03</ram:URIID></ram:URIUniversalCommunication>
      </ram:BuyerTradeParty>
    </ram:ApplicableHeaderTradeAgreement>
    <ram:ApplicableHeaderTradeDelivery>
      <ram:ActualDeliverySupplyChainEvent><ram:OccurrenceDateTime><udt:DateTimeString format="102">20261120</udt:DateTimeString></ram:OccurrenceDateTime></ram:ActualDeliverySupplyChainEvent>
    </ram:ApplicableHeaderTradeDelivery>
    <ram:ApplicableHeaderTradeSettlement>
      <ram:PaymentReference>RE-2026-0001</ram:PaymentReference>
      <ram:InvoiceCurrencyCode>EUR</ram:InvoiceCurrencyCode>
      <ram:SpecifiedTradeSettlementPaymentMeans>
        <ram:TypeCode>58</ram:TypeCode>
        <ram:PayeePartyCreditorFinancialAccount><ram:IBANID>DE02120300000000202051</ram:IBANID><ram:AccountName>Beispiel GmbH</ram:AccountName></ram:PayeePartyCreditorFinancialAccount>
      </ram:SpecifiedTradeSettlementPaymentMeans>
      <ram:ApplicableTradeTax>
        <ram:CalculatedAmount>161.50</ram:CalculatedAmount><ram:TypeCode>VAT</ram:TypeCode>
        <ram:BasisAmount>850.00</ram:BasisAmount><ram:CategoryCode>S</ram:CategoryCode>
        <ram:RateApplicablePercent>19</ram:RateApplicablePercent>
      </ram:ApplicableTradeTax>
      <ram:SpecifiedTradePaymentTerms>
        <ram:Description>Zahlbar innerhalb von 14 Tagen ohne Abzug.</ram:Description>
        <ram:DueDateDateTime><udt:DateTimeString format="102">20261207</udt:DateTimeString></ram:DueDateDateTime>
      </ram:SpecifiedTradePaymentTerms>
      <ram:SpecifiedTradeSettlementHeaderMonetarySummation>
        <ram:LineTotalAmount>850.00</ram:LineTotalAmount>
        <ram:TaxBasisTotalAmount>850.00</ram:TaxBasisTotalAmount>
        <ram:TaxTotalAmount currencyID="EUR">161.50</ram:TaxTotalAmount>
        <ram:GrandTotalAmount>1011.50</ram:GrandTotalAmount>
        <ram:DuePayableAmount>1011.50</ram:DuePayableAmount>
      </ram:SpecifiedTradeSettlementHeaderMonetarySummation>
    </ram:ApplicableHeaderTradeSettlement>
  </rsm:SupplyChainTradeTransaction>
</rsm:CrossIndustryInvoice>
```

---

## 10. Quellen

- KoSIT / XRechnung: https://xeinkauf.de/xrechnung/ · Versionen und Bundles: https://xeinkauf.de/xrechnung/versionen-und-bundles/
- KoSIT Validator: https://github.com/itplr-kosit/validator (CLI: `docs/cli.md`), Releases: https://github.com/itplr-kosit/validator/releases
- Validator-Konfiguration: https://github.com/itplr-kosit/validator-configuration-xrechnung (`docs/usage.md`, `scenarios.xml`)
- XRechnung-Schematron: https://github.com/itplr-kosit/xrechnung-schematron (`src/validation/schematron/common.sch`, `ubl/XRechnung-UBL-validation.sch`, `src/xsl/rule-list.xml`, `CHANGELOG.md`)
- Visualisierung: https://github.com/itplr-kosit/xrechnung-visualization · Testsuite: https://github.com/itplr-kosit/xrechnung-testsuite (Original: https://projekte.kosit.org/xrechnung)
- EN 16931 Validierungsartefakte (CEN TC 434): https://github.com/ConnectingEurope/eInvoicing-EN16931
- E-Rechnung Bund (ZRE, FAQ zu ZUGFeRD XRECHNUNG): https://e-rechnung-bund.de/
- FeRD / ZUGFeRD 2.5.2: https://www.ferd-net.de/en/downloads/publications/details/zugferd-252-english · Factur-X: https://fnfe-mpe.org/factur-x/
- BMF-Schreiben E-Rechnung (15.10.2024, 15.10.2025, 15.10.2026): https://www.bundesfinanzministerium.de (Suche „E-Rechnung“); Zusammenfassung des dritten Schreibens: https://blogs.pwc.de/de/steuern-und-recht/article/257709/
- factur-x (Python): https://github.com/akretion/factur-x · drafthorse: https://github.com/pretix/python-drafthorse · pikepdf: https://github.com/pikepdf/pikepdf
- Mustangproject (Java): https://www.mustangproject.org/
- LibreOffice PDF-Export-Optionen: https://help.libreoffice.org/latest/en-US/text/shared/guide/pdf_params.html (unverifiziert, ob alle Parameter dort dokumentiert sind)
- Peppol BIS Billing 3.0 (Codelisten EAS, Regeln R010/R020): https://docs.peppol.eu/poacc/billing/3.0/
