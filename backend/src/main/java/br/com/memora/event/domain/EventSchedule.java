package br.com.memora.event.domain;

import br.com.memora.shared.error.BusinessRuleException;
import br.com.memora.shared.error.ValidationException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * Quando o evento acontece. Datas e horários "de parede" (sem fuso); horários são opcionais.
 *
 * <p>Regras sempre válidas: a data de início é obrigatória; o fim nunca é anterior ao início. Um horário
 * de término sem data de término se refere ao próprio dia de início. A regra "o início não pode estar no
 * passado" depende do momento da operação e fica em {@link #requireNotInPast(LocalDate)}.
 */
public record EventSchedule(LocalDate startDate, LocalTime startTime, LocalDate endDate, LocalTime endTime) {

    public EventSchedule {
        // A precisão é de minutos: segundos enviados são descartados.
        startTime = startTime == null ? null : startTime.truncatedTo(ChronoUnit.MINUTES);
        endTime = endTime == null ? null : endTime.truncatedTo(ChronoUnit.MINUTES);
        if (startDate == null) {
            throw new ValidationException("EVENT_START_REQUIRED", "Informe a data de início do evento.");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw endBeforeStart();
        }
        boolean endsOnStartDay = endDate == null || endDate.equals(startDate);
        if (endsOnStartDay && startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw endBeforeStart();
        }
    }

    /**
     * @param earliestAllowed primeiro dia aceito como início (o "hoje" mais atrasado do planeta, para não
     *                        recusar o dia de hoje de quem está num fuso diferente do servidor)
     */
    public void requireNotInPast(LocalDate earliestAllowed) {
        if (startDate.isBefore(earliestAllowed)) {
            throw new BusinessRuleException("EVENT_START_IN_PAST", "A data de início não pode estar no passado.");
        }
    }

    private static BusinessRuleException endBeforeStart() {
        return new BusinessRuleException("EVENT_END_BEFORE_START", "O término não pode ser anterior ao início.");
    }
}
