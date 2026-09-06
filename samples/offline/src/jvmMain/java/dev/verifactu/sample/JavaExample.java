package dev.verifactu.sample;

import dev.verifactu.aeat.AeatAdvancedEndpointConfiguration;
import dev.verifactu.aeat.AeatCertificateAccess;
import dev.verifactu.aeat.AeatEndpointConfiguration;
import dev.verifactu.aeat.AeatEnvironment;
import dev.verifactu.aeat.AeatResponseParseResult;
import dev.verifactu.aeat.AeatResponseParser;
import dev.verifactu.aeat.AeatTransportRequest;
import dev.verifactu.aeat.AeatTransportResult;
import dev.verifactu.core.ChainState;
import dev.verifactu.core.FiscalAmount;
import dev.verifactu.core.FiscalRecordFactory;
import dev.verifactu.core.InvoiceIdentifier;
import dev.verifactu.core.InvoiceIssueDate;
import dev.verifactu.core.InvoiceNumber;
import dev.verifactu.core.InvoiceType;
import dev.verifactu.core.Qualification;
import dev.verifactu.core.RecordCreationResult;
import dev.verifactu.core.RecordGenerationTimestamp;
import dev.verifactu.core.RecordVersion;
import dev.verifactu.core.RegistroAlta;
import dev.verifactu.core.RegistroAltaDraft;
import dev.verifactu.core.SistemaInformatico;
import dev.verifactu.core.TaxBreakdown;
import dev.verifactu.core.TaxBreakdownDetail;
import dev.verifactu.core.TaxIdentifier;
import dev.verifactu.core.TaxOperation;
import dev.verifactu.core.TaxType;
import dev.verifactu.core.ValueResult;
import dev.verifactu.qr.QrEnvironment;
import dev.verifactu.qr.QrPayloadBuilder;
import dev.verifactu.qr.QrPayloadInput;
import dev.verifactu.qr.QrPayloadResult;
import dev.verifactu.testkit.AeatResponseFixtures;
import dev.verifactu.testkit.AeatResponseScenario;
import dev.verifactu.testkit.FakeAeatTransport;
import dev.verifactu.xml.RegistroXmlSerializer;
import dev.verifactu.xml.SubmissionBatchXmlSerializer;
import dev.verifactu.xml.SubmissionHeader;
import dev.verifactu.xml.SubmissionRecord;
import java.util.List;

/** A Java 11 consumer using static entry points and ordinary typed result getters. */
public final class JavaExample {
    private JavaExample() {}

    public static void main(String[] args) {
        TaxIdentifier issuer = value(TaxIdentifier.parse("89890001K"));
        InvoiceIdentifier invoice = new InvoiceIdentifier(
            issuer, value(InvoiceNumber.parse("JAVA-001")), value(InvoiceIssueDate.parse("01-01-2024"))
        );
        TaxBreakdownDetail detail = new TaxBreakdownDetail(
            new TaxOperation.Qualified(Qualification.SUBJECT_NOT_EXEMPT), value(FiscalAmount.parse("100.00")),
            TaxType.IVA, "01", "21", null, value(FiscalAmount.parse("21.00")), null, null
        );
        RegistroAltaDraft draft = new RegistroAltaDraft(
            RecordVersion.V1_0, invoice, "Example issuer", InvoiceType.F2,
            value(FiscalAmount.parse("21.00")), value(FiscalAmount.parse("121.00")), "Synthetic Java example",
            new TaxBreakdown(List.of(detail)), ChainState.FirstRecord.INSTANCE,
            new SistemaInformatico("Example producer", issuer, "Java sample", "VF", "0.1", "sample-1", true, false, false),
            value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00"))
        );
        RecordCreationResult<RegistroAlta> result = FiscalRecordFactory.createRegistration(draft);
        if (!(result instanceof RecordCreationResult.Created)) {
            throw new IllegalStateException("Synthetic Java draft failed local validation");
        }
        RecordCreationResult.Created<RegistroAlta> created = (RecordCreationResult.Created<RegistroAlta>) result;
        RegistroAlta record = created.getRecord();
        String recordXml = RegistroXmlSerializer.serialize(record);
        QrPayloadResult qr = QrPayloadBuilder.build(new QrPayloadInput(invoice, draft.getTotalAmount(), QrEnvironment.TEST));
        if (!(qr instanceof QrPayloadResult.Created) || !recordXml.contains(record.getHash())) {
            throw new IllegalStateException("Synthetic artifacts were not produced");
        }
        String batchXml = SubmissionBatchXmlSerializer.serialize(
            new SubmissionHeader(draft.getIssuerName(), issuer), List.of(new SubmissionRecord.Registration(record))
        );
        FakeAeatTransport transport = new FakeAeatTransport(List.of(AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED)));
        AeatTransportResult delivered = transport.execute(new AeatTransportRequest(
            AeatEndpointConfiguration.submissionEndpointWithAdvancedOverride(
                AeatEnvironment.TEST, AeatCertificateAccess.STANDARD,
                new AeatAdvancedEndpointConfiguration("https://example.invalid/verifactu")
            ), batchXml
        ));
        AeatResponseParseResult parsed = AeatResponseParser.parseSubmission(((AeatTransportResult.XmlResponse) delivered).getXml());
        if (!(parsed instanceof AeatResponseParseResult.Parsed)) {
            throw new IllegalStateException("Synthetic response could not be parsed");
        }
        System.out.println("Java registration: " + ((AeatResponseParseResult.Parsed) parsed).getResponse().getStatus());
        System.out.println("Next synthetic chain hash: " + created.getNextChainState().getHash());
        System.out.println("Captured fake requests: " + transport.getRequests().size() + "; network calls: 0");
    }

    private static <T> T value(ValueResult<T> result) {
        if (result instanceof ValueResult.Valid) {
            return ((ValueResult.Valid<T>) result).getValue();
        }
        throw new IllegalArgumentException("Invalid synthetic Java fixture value");
    }
}
