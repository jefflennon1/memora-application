package br.com.memora.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Página de resultados no formato público da API ({@code ?page=0&size=20}).
 * Nunca expõe o {@link Page} do Spring Data diretamente.
 */
@Schema(name = "Page", description = "Página de resultados")
public record PageResponse<T>(
        @Schema(description = "Itens desta página") List<T> items,
        @Schema(description = "Número da página (começa em 0)", example = "0") int page,
        @Schema(description = "Tamanho da página", example = "20") int size,
        @Schema(description = "Total de itens", example = "42") long totalItems,
        @Schema(description = "Total de páginas", example = "3") int totalPages,
        @Schema(description = "Se existe uma próxima página") boolean hasNext) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 50;

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    /** Pedido de página a partir dos parâmetros da URL; valores fora da faixa são ajustados. */
    public static Pageable request(int page, int size) {
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize);
    }
}
