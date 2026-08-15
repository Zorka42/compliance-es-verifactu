# Integration Flow

This document describes how a host application should integrate VeriFactu KMP once implementation exists.

## Registration Flow

1. The host application decides that an invoice is legally issued.
2. The host application loads the current chain head for the relevant SIF/taxpayer chain.
3. The host application calls the library to create `RegistroAlta`.
4. The library calculates the hash and returns an immutable fiscal record.
5. The host application validates the record.
6. The host application persists its invoice data, fiscal record, and next chain state according to its own durability requirements.
7. The host application renders the invoice and QR payload.
8. The host application queues or submits the fiscal record according to its VERI*FACTU operating model.

## Cancellation Flow

1. The host application decides that an invoice must be cancelled according to business/legal rules.
2. The host application loads the current chain head.
3. The host application calls the library to create `RegistroAnulacion`.
4. The cancellation record is chained like any other fiscal record.
5. The host application persists and submits the cancellation record.

## Submission Flow

1. The host application selects AEAT test or production environment.
2. The host application provides immutable records and credential access.
3. The library builds and sends the SOAP request.
4. The library parses global and per-record AEAT responses.
5. The host application stores submission results and acts on flow-control, incidence, retry, or correction requirements.

## Failure Flow

Network failures are not equivalent to AEAT rejections.

If delivery is unknown, the host application should retry the same immutable fiscal records until a response is obtained, following AEAT guidance and flow-control rules.

The library must never create a new fiscal record merely because a transport response was lost.
