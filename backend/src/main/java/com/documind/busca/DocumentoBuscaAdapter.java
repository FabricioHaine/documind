package com.documind.busca;

import com.documind.common.PageResponse;
import com.documind.documento.DocumentoResponse;
import com.documind.documento.DocumentoService;
import com.documind.usuario.Usuario;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Adaptador fino que expoe a listagem de DocumentoService ao modulo de
 * busca, evitando acoplamento direto controller-busca → servico de
 * documentos por interface maior que o necessario.
 */
@Component
public class DocumentoBuscaAdapter {

    private final DocumentoService documentoService;

    public DocumentoBuscaAdapter(DocumentoService documentoService) {
        this.documentoService = documentoService;
    }

    public PageResponse<DocumentoResponse> listar(Usuario usuario, String nome, String tipo, String estado,
                                                  Long colecaoId, LocalDate data, Pageable pageable) {
        return documentoService.listar(usuario, nome, tipo, estado, colecaoId, data, pageable);
    }
}
