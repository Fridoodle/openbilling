package openbilling.xml;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import openbilling.model.Berechnung;
import openbilling.model.Berechnung.Ergebnis;
import openbilling.model.Berechnung.Steuergruppe;
import openbilling.model.Rechnung;
import openbilling.model.Rechnung.Adresse;
import openbilling.model.Rechnung.Partei;
import openbilling.model.Rechnung.Position;
import openbilling.model.Rechnung.Steuerkategorie;

/**
 * Schreibt eine {@link Rechnung} als UN/CEFACT CII D16B (XRechnung-Syntax, einbettbar als Factur-X).
 * Die Elementreihenfolge folgt der XSD-Sequenz; Vorbild ist tests/fixtures/minimal-cii.xml.
 * Unterstützt derzeit Überweisung (BT-81 = 30/58); Karte und Lastschrift fehlen noch.
 */
public final class CiiSchreiber {

    private static final String RSM = "urn:un:unece:uncefact:data:standard:CrossIndustryInvoice:100";
    private static final String RAM = "urn:un:unece:uncefact:data:standard:ReusableAggregateBusinessInformationEntity:100";
    private static final String UDT = "urn:un:unece:uncefact:data:standard:UnqualifiedDataType:100";
    private static final DateTimeFormatter FORMAT_102 = DateTimeFormatter.BASIC_ISO_DATE;

    private final XMLStreamWriter w;
    private final Rechnung r;
    private final Ergebnis e;

    private CiiSchreiber(XMLStreamWriter w, Rechnung r) {
        this.w = w;
        this.r = r;
        this.e = Berechnung.berechne(r);
    }

    public static String schreibe(Rechnung r) {
        if (!"30".equals(r.zahlung().art()) && !"58".equals(r.zahlung().art())) {
            throw new IllegalArgumentException("Zahlungsart noch nicht unterstützt (BT-81): " + r.zahlung().art());
        }
        StringWriter out = new StringWriter();
        try {
            XMLStreamWriter w = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            new CiiSchreiber(w, r).dokument();
            w.close();
        } catch (XMLStreamException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toString();
    }

    private void dokument() throws XMLStreamException {
        w.writeStartDocument("UTF-8", "1.0");
        w.setPrefix("rsm", RSM);
        w.setPrefix("ram", RAM);
        w.setPrefix("udt", UDT);
        w.writeStartElement(RSM, "CrossIndustryInvoice");
        w.writeNamespace("rsm", RSM);
        w.writeNamespace("ram", RAM);
        w.writeNamespace("udt", UDT);

        w.writeStartElement(RSM, "ExchangedDocumentContext");
        if (r.prozess() != null) {
            start("BusinessProcessSpecifiedDocumentContextParameter");
            text("ID", r.prozess()); // BT-23
            end();
        }
        start("GuidelineSpecifiedDocumentContextParameter");
        text("ID", r.spezifikation()); // BT-24
        end();
        w.writeEndElement();

        w.writeStartElement(RSM, "ExchangedDocument");
        text("ID", r.nummer()); // BT-1
        text("TypeCode", r.typ()); // BT-3
        datum("IssueDateTime", r.datum()); // BT-2
        w.writeEndElement();

        w.writeStartElement(RSM, "SupplyChainTradeTransaction");
        for (int i = 0; i < r.positionen().size(); i++) {
            position(r.positionen().get(i), e.zeilenbetraege().get(i));
        }
        vereinbarung();
        lieferung();
        abrechnung();
        w.writeEndElement();

        w.writeEndElement();
        w.writeEndDocument();
    }

    /** BG-25 */
    private void position(Position p, BigDecimal zeilenbetrag) throws XMLStreamException {
        start("IncludedSupplyChainTradeLineItem");
        start("AssociatedDocumentLineDocument");
        text("LineID", p.id()); // BT-126
        end();
        start("SpecifiedTradeProduct");
        text("Name", p.bezeichnung()); // BT-153
        end();
        start("SpecifiedLineTradeAgreement");
        start("NetPriceProductTradePrice");
        text("ChargeAmount", p.nettopreis().toPlainString()); // BT-146
        if (p.basismenge() != null) {
            start("BasisQuantity"); // BT-149/150
            w.writeAttribute("unitCode", p.einheit());
            w.writeCharacters(p.basismenge().toPlainString());
            end();
        }
        end();
        end();
        start("SpecifiedLineTradeDelivery");
        start("BilledQuantity"); // BT-129/130
        w.writeAttribute("unitCode", p.einheit());
        w.writeCharacters(p.menge().toPlainString());
        end();
        end();
        start("SpecifiedLineTradeSettlement");
        start("ApplicableTradeTax");
        text("TypeCode", "VAT");
        text("CategoryCode", p.kategorie().name()); // BT-151
        if (p.kategorie() != Steuerkategorie.O) {
            text("RateApplicablePercent", satz(p.steuersatz())); // BT-152
        }
        end();
        start("SpecifiedTradeSettlementLineMonetarySummation");
        text("LineTotalAmount", zeilenbetrag.toPlainString()); // BT-131
        end();
        end();
        end();
    }

    private void vereinbarung() throws XMLStreamException {
        start("ApplicableHeaderTradeAgreement");
        text("BuyerReference", r.kaeuferReferenz()); // BT-10
        partei("SellerTradeParty", r.verkaeufer()); // BG-4
        partei("BuyerTradeParty", r.kaeufer()); // BG-7
        end();
    }

    private void partei(String element, Partei p) throws XMLStreamException {
        start(element);
        if (p.kennung() != null) {
            text("ID", p.kennung()); // BT-29 / BT-46
        }
        text("Name", p.name());
        if (p.kontakt() != null) { // BG-6 / BG-9
            start("DefinedTradeContact");
            if (p.kontakt().name() != null) {
                text("PersonName", p.kontakt().name()); // BT-41 / BT-56
            }
            if (p.kontakt().telefon() != null) {
                start("TelephoneUniversalCommunication");
                text("CompleteNumber", p.kontakt().telefon()); // BT-42 / BT-57
                end();
            }
            if (p.kontakt().email() != null) {
                start("EmailURIUniversalCommunication");
                text("URIID", p.kontakt().email()); // BT-43 / BT-58
                end();
            }
            end();
        }
        adresse(p.adresse());
        start("URIUniversalCommunication"); // BT-34 / BT-49
        start("URIID");
        w.writeAttribute("schemeID", p.elektronischeAdresse().schema());
        w.writeCharacters(p.elektronischeAdresse().wert());
        end();
        end();
        steuerregistrierung("FC", p.steuernummer()); // BT-32
        steuerregistrierung("VA", p.ustId()); // BT-31 / BT-48
        end();
    }

    private void adresse(Adresse a) throws XMLStreamException {
        start("PostalTradeAddress");
        text("PostcodeCode", a.plz());
        if (a.strasse() != null) {
            text("LineOne", a.strasse());
        }
        text("CityName", a.ort());
        text("CountryID", a.land());
        end();
    }

    private void steuerregistrierung(String schema, String wert) throws XMLStreamException {
        if (wert == null) {
            return;
        }
        start("SpecifiedTaxRegistration");
        start("ID");
        w.writeAttribute("schemeID", schema);
        w.writeCharacters(wert);
        end();
        end();
    }

    private void lieferung() throws XMLStreamException {
        start("ApplicableHeaderTradeDelivery");
        if (r.lieferdatum() != null) { // BT-72
            start("ActualDeliverySupplyChainEvent");
            datum("OccurrenceDateTime", r.lieferdatum());
            end();
        }
        end();
    }

    private void abrechnung() throws XMLStreamException {
        start("ApplicableHeaderTradeSettlement");
        if (r.zahlung().verwendungszweck() != null) {
            text("PaymentReference", r.zahlung().verwendungszweck()); // BT-83
        }
        text("InvoiceCurrencyCode", r.waehrung()); // BT-5

        start("SpecifiedTradeSettlementPaymentMeans"); // BG-16
        text("TypeCode", r.zahlung().art()); // BT-81
        start("PayeePartyCreditorFinancialAccount"); // BG-17
        text("IBANID", r.zahlung().iban()); // BT-84
        if (r.zahlung().kontoinhaber() != null) {
            text("AccountName", r.zahlung().kontoinhaber()); // BT-85
        }
        end();
        if (r.zahlung().bic() != null) {
            start("PayeeSpecifiedCreditorFinancialInstitution");
            text("BICID", r.zahlung().bic()); // BT-86
            end();
        }
        end();

        for (Steuergruppe g : e.steuergruppen()) { // BG-23
            start("ApplicableTradeTax");
            text("CalculatedAmount", g.steuer().toPlainString()); // BT-117
            text("TypeCode", "VAT");
            if (g.kategorie() != Steuerkategorie.S && r.befreiungsgrund() != null) {
                text("ExemptionReason", r.befreiungsgrund()); // BT-120
            }
            text("BasisAmount", g.basis().toPlainString()); // BT-116
            text("CategoryCode", g.kategorie().name()); // BT-118
            if (g.kategorie() != Steuerkategorie.O) {
                text("RateApplicablePercent", g.satz().toPlainString()); // BT-119
            }
            end();
        }

        if (r.zahlungsbedingungen() != null || r.faelligAm() != null) {
            start("SpecifiedTradePaymentTerms");
            if (r.zahlungsbedingungen() != null) {
                text("Description", r.zahlungsbedingungen()); // BT-20
            }
            if (r.faelligAm() != null) {
                datum("DueDateDateTime", r.faelligAm()); // BT-9
            }
            end();
        }

        start("SpecifiedTradeSettlementHeaderMonetarySummation"); // BG-22
        text("LineTotalAmount", e.summeZeilen().toPlainString()); // BT-106
        text("TaxBasisTotalAmount", e.netto().toPlainString()); // BT-109
        start("TaxTotalAmount"); // BT-110
        w.writeAttribute("currencyID", r.waehrung());
        w.writeCharacters(e.steuer().toPlainString());
        end();
        text("GrandTotalAmount", e.brutto().toPlainString()); // BT-112
        if (e.vorausbezahlt().signum() != 0) {
            text("TotalPrepaidAmount", e.vorausbezahlt().toPlainString()); // BT-113
        }
        text("DuePayableAmount", e.zahlbar().toPlainString()); // BT-115
        end();

        end();
    }

    private void datum(String element, LocalDate d) throws XMLStreamException {
        start(element);
        w.writeStartElement(UDT, "DateTimeString");
        w.writeAttribute("format", "102");
        w.writeCharacters(d.format(FORMAT_102));
        w.writeEndElement();
        end();
    }

    private static String satz(BigDecimal s) {
        BigDecimal n = s.stripTrailingZeros();
        return (n.scale() < 0 ? n.setScale(0) : n).toPlainString();
    }

    private void start(String name) throws XMLStreamException {
        w.writeStartElement(RAM, name);
    }

    private void end() throws XMLStreamException {
        w.writeEndElement();
    }

    private void text(String name, String wert) throws XMLStreamException {
        start(name);
        w.writeCharacters(wert);
        end();
    }
}
