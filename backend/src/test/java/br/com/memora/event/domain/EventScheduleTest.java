package br.com.memora.event.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.memora.shared.error.DomainException;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class EventScheduleTest {

    private static final LocalDate DAY = LocalDate.of(2026, 12, 5);
    private static final LocalTime THREE_PM = LocalTime.of(15, 0);
    private static final LocalTime SIX_PM = LocalTime.of(18, 0);

    @Test
    void startDateIsRequired() {
        assertCode(() -> new EventSchedule(null, null, null, null), "EVENT_START_REQUIRED");
    }

    @Test
    void endDateCannotPrecedeStartDate() {
        assertCode(() -> new EventSchedule(DAY, null, DAY.minusDays(1), null), "EVENT_END_BEFORE_START");
    }

    @Test
    void endTimeOnTheStartDayCannotPrecedeStartTime() {
        assertCode(() -> new EventSchedule(DAY, SIX_PM, null, THREE_PM), "EVENT_END_BEFORE_START");
        assertCode(() -> new EventSchedule(DAY, SIX_PM, DAY, THREE_PM), "EVENT_END_BEFORE_START");
    }

    @Test
    void multiDayEventMayEndEarlierInTheDay() {
        assertThatCode(() -> new EventSchedule(DAY, SIX_PM, DAY.plusDays(1), THREE_PM)).doesNotThrowAnyException();
        assertThatCode(() -> new EventSchedule(DAY, THREE_PM, null, SIX_PM)).doesNotThrowAnyException();
    }

    @Test
    void startInThePastIsRejectedButTodayIsAccepted() {
        EventSchedule schedule = new EventSchedule(DAY, null, null, null);
        assertThatCode(() -> schedule.requireNotInPast(DAY)).doesNotThrowAnyException();
        assertCode(() -> schedule.requireNotInPast(DAY.plusDays(1)), "EVENT_START_IN_PAST");
    }

    @Test
    void locationIsDroppedForNonInPersonEvents() {
        EventLocation online = new EventLocation(EventModality.ONLINE, "Belo Horizonte", "Rua A, 1");
        assertThat(online.city()).isNull();
        assertThat(online.address()).isNull();

        EventLocation inPerson = new EventLocation(EventModality.IN_PERSON, "  Belo Horizonte ", " ");
        assertThat(inPerson.city()).isEqualTo("Belo Horizonte");
        assertThat(inPerson.address()).isNull();
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(code);
    }
}
