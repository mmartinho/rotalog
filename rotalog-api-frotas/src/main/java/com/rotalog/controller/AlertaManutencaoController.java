package com.rotalog.controller;

import com.rotalog.domain.StatusAlerta;
import com.rotalog.dto.AlertaManutencaoResponse;
import com.rotalog.dto.ResumoVerificacaoAlertas;
import com.rotalog.service.AlertaManutencaoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AlertaManutencaoController - alertas de manutenção preventiva
 *
 * FIXME: Sem @ControllerAdvice (mesmo padrão de tratamento de erro dos demais controllers)
 */
@Slf4j
@RestController
@RequestMapping("/manutencoes/alertas")
public class AlertaManutencaoController {

    @Autowired
    private AlertaManutencaoService alertaManutencaoService;

    @PostMapping("/verificar")
    public ResponseEntity<?> verificar() {
        try {
            ResumoVerificacaoAlertas resumo = alertaManutencaoService.verificarAlertas();
            return ResponseEntity.ok(resumo);
        } catch (RuntimeException e) {
            log.error("Erro ao verificar alertas de manutenção: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String status) {
        try {
            StatusAlerta statusAlerta = (status == null || status.trim().isEmpty())
                    ? null
                    : AlertaManutencaoService.converterStatus(status);
            List<AlertaManutencaoResponse> alertas = alertaManutencaoService.listarAlertas(statusAlerta);
            return ResponseEntity.ok(alertas);
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("erro", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        }
    }
}
