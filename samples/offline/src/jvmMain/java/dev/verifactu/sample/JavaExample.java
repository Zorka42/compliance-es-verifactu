package dev.verifactu.sample;

import dev.verifactu.aeat.AeatAdvancedEndpointConfiguration;
import dev.verifactu.aeat.AeatCertificateAccess;
import dev.verifactu.aeat.AeatEndpointConfiguration;
import dev.verifactu.aeat.AeatEnvironment;
import dev.verifactu.aeat.AeatOperationType;
import dev.verifactu.aeat.AeatRecordStatus;
import dev.verifactu.aeat.AeatResponseCorrelation;
import dev.verifactu.aeat.AeatResponseCorrelationResult;
import dev.verifactu.aeat.AeatResponseLine;
import dev.verifactu.aeat.AeatResponseParseResult;
import dev.verifactu.aeat.AeatResponseParser;
import dev.verifactu.aeat.AeatSubmissionResponse;
import dev.verifactu.aeat.AeatSubmissionStatus;
import dev.verifactu.aeat.AeatTransportRequest;
import dev.verifactu.aeat.AeatTransportResult;
import dev.verifactu.aeat.FiscalSubmissionPreparation;
import dev.verifactu.aeat.RegistrationPreparationResult;
import dev.verifactu.core.ChainState;
import dev.verifactu.core.FiscalAmount;
import dev.verifactu.core.FiscalIdentifierValidator;
import dev.verifactu.core.FiscalParty;
import dev.verifactu.core.FiscalPartyIdentifier;
import dev.verifactu.core.InvoiceIdentifier;
import dev.verifactu.core.InvoiceIssueDate;
import dev.verifactu.core.InvoiceNumber;
import dev.verifactu.core.InvoiceType;
import dev.verifactu.core.Qualification;
import dev.verifactu.core.RecordGenerationTimestamp;
import dev.verifactu.core.RecordVersion;
import dev.verifactu.core.RegistrationConditionalData;
import dev.verifactu.core.RegistroAlta;
import dev.verifactu.core.RegistroAltaDraft;
import dev.verifactu.core.SistemaInformatico;
import dev.verifactu.core.TaxBreakdown;
import dev.verifactu.core.TaxBreakdownDetail;
import dev.verifactu.core.TaxIdentifier;
import dev.verifactu.core.TaxOperation;
import dev.verifactu.core.TaxType;
import dev.verifactu.core.ValueResult;
import dev.verifactu.core.ValidationContext;
import dev.verifactu.qr.QrEnvironment;
import dev.verifactu.testkit.AeatResponseFixtures;
import dev.verifactu.testkit.AeatResponseScenario;
import dev.verifactu.testkit.FakeAeatTransport;
import dev.verifactu.xml.SubmissionRecord;
import java.util.ArrayList;
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
        FiscalParty recipient = new FiscalParty(
            "Synthetic Java recipient", new FiscalPartyIdentifier.SpanishNif(value(TaxIdentifier.parse("89890002E")))
        );
        List<FiscalParty> recipients = new ArrayList<>(List.of(recipient));
        RegistroAltaDraft draft = new RegistroAltaDraft(
            RecordVersion.V1_0, invoice, "Example issuer", InvoiceType.F1,
            value(FiscalAmount.parse("21.00")), value(FiscalAmount.parse("121.00")), "Synthetic Java example",
            new TaxBreakdown(List.of(detail)), ChainState.FirstRecord.INSTANCE,
            new SistemaInformatico("Example producer", issuer, "Java sample", "VF", "0.1", "sample-1", true, false, false),
            value(RecordGenerationTimestamp.parse("2024-01-01T12:00:00+01:00")),
            new RegistrationConditionalData(
                null, null, null, null, List.of(), null, null, null, null, null, null, recipients, null, null, null
            )
        );
        RegistrationPreparationResult result = FiscalSubmissionPreparation.prepareRegistration(draft, QrEnvironment.TEST);
        RegistrationPreparationResult withContext = FiscalSubmissionPreparation.prepareRegistration(
            draft, QrEnvironment.TEST, new ValidationContext(value(InvoiceIssueDate.parse("01-01-2027")))
        );
        if (!(withContext instanceof RegistrationPreparationResult.Prepared)
                || !FiscalIdentifierValidator.validateTaxIdentifier(issuer).isValid()
                || new ValidationContext().getAeatReceiptDate() != null) {
            throw new IllegalStateException("Java identifier or explicit-context entry points failed");
        }
        if (!(result instanceof RegistrationPreparationResult.Prepared)) {
            throw new IllegalStateException("Synthetic Java draft failed local validation");
        }
        RegistrationPreparationResult.Prepared created = (RegistrationPreparationResult.Prepared) result;
        RegistroAlta record = created.getRecord();
        recipients.clear();
        List<FiscalParty> preparedRecipients = record.getDraft().getConditionalData().getRecipients();
        assertReadOnly(preparedRecipients, recipient);
        if (!preparedRecipients.equals(List.of(recipient))) {
            throw new IllegalStateException("Prepared recipients changed after caller or getter mutation");
        }
        List<TaxBreakdownDetail> preparedDetails = record.getDraft().getTaxBreakdown().getDetails();
        try {
            preparedDetails.clear();
            throw new IllegalStateException("Prepared fiscal record allowed Java list mutation");
        } catch (UnsupportedOperationException expected) {
            // Java collection mutators must not change a prepared fiscal record.
        }
        try {
            preparedDetails.set(0, detail);
            throw new IllegalStateException("Prepared fiscal record allowed Java list replacement");
        } catch (UnsupportedOperationException expected) {
            // Even replacement with an equal value must be unavailable.
        }
        if (!preparedDetails.equals(List.of(detail))) {
            throw new IllegalStateException("Prepared tax breakdown changed after Java mutation attempts");
        }
        if (!created.getRecordXml().contains(record.getHash()) || !created.getQr().getUrl().contains("numserie=JAVA-001")) {
            throw new IllegalStateException("Synthetic artifacts were not produced");
        }
        List<SubmissionRecord> submitted = List.of(new SubmissionRecord.Registration(record));
        // A real host persists this exact payload, the record, and the next head before delivery.
        String savedPayload = created.getSoapEnvelope();
        FakeAeatTransport transport = new FakeAeatTransport(List.of(
            AeatResponseFixtures.response(AeatResponseScenario.ACCEPTED, invoice, AeatOperationType.REGISTRATION)
        ));
        AeatTransportResult delivered = transport.execute(new AeatTransportRequest(
            AeatEndpointConfiguration.submissionEndpointWithAdvancedOverride(
                AeatEnvironment.TEST, AeatCertificateAccess.STANDARD,
                new AeatAdvancedEndpointConfiguration("https://example.invalid/verifactu")
            ), savedPayload
        ));
        if (!(delivered instanceof AeatTransportResult.XmlResponse)) {
            throw new IllegalStateException("Synthetic transport did not return XML; no acceptance established");
        }
        AeatTransportResult.XmlResponse xmlResponse = (AeatTransportResult.XmlResponse) delivered;
        if (xmlResponse.getStatusCode() < 200 || xmlResponse.getStatusCode() >= 300) {
            throw new IllegalStateException("Synthetic HTTP response did not establish acceptance");
        }
        AeatResponseParseResult parsed = AeatResponseParser.parseSubmission(xmlResponse.getXml());
        if (!(parsed instanceof AeatResponseParseResult.Parsed)) {
            throw new IllegalStateException("Synthetic response could not be parsed");
        }
        AeatSubmissionResponse response = ((AeatResponseParseResult.Parsed) parsed).getResponse();
        AeatResponseCorrelationResult correlation = AeatResponseCorrelation.correlate(submitted, response);
        if (!(correlation instanceof AeatResponseCorrelationResult.Matched) || response.getStatus() != AeatSubmissionStatus.ACCEPTED) {
            throw new IllegalStateException("Response identity, operation, or aggregate state did not establish acceptance");
        }
        List<AeatResponseLine> lines = ((AeatResponseCorrelationResult.Matched) correlation).getLines();
        if (lines.size() != 1 || lines.get(0).getStatus() != AeatRecordStatus.ACCEPTED
                || lines.get(0).getDeclaredStatus() != AeatRecordStatus.ACCEPTED) {
            throw new IllegalStateException("Synthetic response record was not accepted without errors");
        }
        System.out.println("Java registration: " + lines.get(0).getStatus());
        System.out.println("Next synthetic chain hash: " + created.getNextChainState().getHash());
        System.out.println("Captured fake requests: " + transport.getRequests().size() + "; network calls: 0");
    }

    private static <T> void assertReadOnly(List<T> values, T item) {
        try {
            values.clear();
            throw new IllegalStateException("Prepared fiscal list allowed Java mutation");
        } catch (UnsupportedOperationException expected) {
            // Prepared lists must also be immutable to Java callers.
        }
        try {
            values.set(0, item);
            throw new IllegalStateException("Prepared fiscal list allowed Java replacement");
        } catch (UnsupportedOperationException expected) {
            // Replacement must not bypass a read-only collection boundary.
        }
    }

    private static <T> T value(ValueResult<T> result) {
        if (result instanceof ValueResult.Valid) {
            return ((ValueResult.Valid<T>) result).getValue();
        }
        throw new IllegalArgumentException("Invalid synthetic Java fixture value");
    }
}
