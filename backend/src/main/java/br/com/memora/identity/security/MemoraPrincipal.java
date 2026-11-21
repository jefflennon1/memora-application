package br.com.memora.identity.security;

import java.util.UUID;

/**
 * Usuário autenticado na sessão, do ponto de vista da Memora: apenas o identificador da conta interna.
 * Os demais dados (estado, @usuário) são sempre lidos do banco, pois podem mudar durante a sessão.
 */
public interface MemoraPrincipal {

    UUID userId();
}
