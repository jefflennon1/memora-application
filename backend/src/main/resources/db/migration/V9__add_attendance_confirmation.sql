-- V9 — Confirmação de presença física (RSVP) em eventos presenciais.

ALTER TABLE event_participant
    ADD COLUMN attendance_status VARCHAR(20),
    ADD COLUMN attendance_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_event_participant_attendance
        CHECK (attendance_status IS NULL OR attendance_status IN ('ATTENDING', 'NOT_ATTENDING')),
    ADD CONSTRAINT ck_event_participant_attendance_date
        CHECK ((attendance_status IS NULL) = (attendance_updated_at IS NULL));

ALTER TABLE join_request
    ADD COLUMN attendance_status VARCHAR(20),
    ADD COLUMN attendance_updated_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_join_request_attendance
        CHECK (attendance_status IS NULL OR attendance_status IN ('ATTENDING', 'NOT_ATTENDING')),
    ADD CONSTRAINT ck_join_request_attendance_date
        CHECK ((attendance_status IS NULL) = (attendance_updated_at IS NULL));

CREATE INDEX ix_event_participant_attendance
    ON event_participant (event_id, attendance_status);

COMMENT ON COLUMN event_participant.attendance_status IS
    'RSVP físico: ATTENDING, NOT_ATTENDING ou NULL enquanto não respondeu/não se aplica.';
COMMENT ON COLUMN join_request.attendance_status IS
    'RSVP informado ao pedir entrada; é transferido ao vínculo quando a solicitação é aprovada.';
