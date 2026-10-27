package com.rotalog.service;

import com.rotalog.domain.Veiculo;
import com.rotalog.repository.VeiculoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * VeiculoService - CRUD, consultas e ativação de veículos.
 * Validação, notificação e manutenção preventiva foram extraídas (ADR 0001, problema 1).
 * Intentional technical debt for the course
 *
 * TODO: Add proper error handling
 */
@Slf4j
@Service
public class VeiculoService {

    @Autowired // FIXME: deveria usar injeção por construtor
    private VeiculoRepository veiculoRepository;

    @Autowired
    private VeiculoValidator veiculoValidator;

    @Autowired
    private VeiculoNotificador veiculoNotificador;

    @Autowired
    private ManutencaoPreventivaService manutencaoPreventivaService;

    /**
     * Lista todos os veículos
     *
     * FIXME: Sem paginação - pode retornar milhares de registros
     * FIXME: Sem cache
     */
    public List<Veiculo> listarTodos() {
        log.info("Listando todos os veículos"); // misturando pt/en nos logs
        return veiculoRepository.findAll();
    }

    /**
     * Busca veículo por ID
     */
    public Veiculo buscarPorId(Long id) {
        return veiculoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado: " + id)); // FIXME: Use proper exception
    }

    /**
     * Busca veículo por placa
     */
    public Veiculo buscarPorPlaca(String placa) {
        return veiculoRepository.findByPlaca(placa)
                .orElseThrow(() -> new RuntimeException("Veículo não encontrado com placa: " + placa));
    }

    /**
     * Registra um novo veículo no sistema
     *
     * FIXME: No proper error handling
     */
    public Veiculo registrarVeiculo(String placa, String modelo, Integer anoFabricacao) {
        veiculoValidator.validarPlaca(placa);

        // Verifica duplicidade
        Optional<Veiculo> existente = veiculoRepository.findByPlaca(placa);
        if (existente.isPresent()) {
            throw new RuntimeException("Veículo com placa " + placa + " já existe"); // FIXME: Use proper exception
        }

        veiculoValidator.validarModelo(modelo);
        veiculoValidator.validarAno(anoFabricacao);

        // Criar veículo
        Veiculo veiculo = new Veiculo();
        veiculo.setPlaca(placa.toUpperCase());
        veiculo.setModelo(modelo);
        veiculo.setAnoFabricacao(anoFabricacao);
        veiculo.setStatus("ATIVO");
        veiculo.setQuilometragem(0L);
        veiculo.setDataCadastro(LocalDateTime.now());
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo salvo = veiculoRepository.save(veiculo);
        log.info("Veículo registrado: {} - {}", salvo.getPlaca(), salvo.getModelo());

        veiculoNotificador.notificar("NOVO_VEICULO",
            "Novo veículo cadastrado: " + salvo.getPlaca() + " - " + salvo.getModelo());

        return salvo;
    }

    /**
     * Atualiza dados do veículo
     *
     * FIXME: Atualiza todos os campos sem verificar quais mudaram
     * FIXME: Sem validação de campos individuais
     */
    public Veiculo atualizarVeiculo(Long id, String modelo, Integer anoFabricacao, Long quilometragem) {
        Veiculo veiculo = buscarPorId(id);

        if (modelo != null && !modelo.isEmpty()) {
            veiculo.setModelo(modelo);
        }
        if (anoFabricacao != null) {
            veiculo.setAnoFabricacao(anoFabricacao);
        }
        if (quilometragem != null) {
            if (quilometragem < veiculo.getQuilometragem()) {
                // FIXME: deveria lançar exceção, não apenas logar
                log.warn("Tentativa de reduzir quilometragem do veículo {}: {} -> {}",
                    id, veiculo.getQuilometragem(), quilometragem);
            }
            veiculo.setQuilometragem(quilometragem);
        }

        veiculo.setDataAtualizacao(LocalDateTime.now());
        return veiculoRepository.save(veiculo);
    }

    /**
     * Atualiza quilometragem do veículo
     *
     * FIXME: No audit trail
     */
    public Veiculo atualizarQuilometragem(Long veiculoId, Long novaQuilometragem) {
        Veiculo veiculo = buscarPorId(veiculoId);

        veiculoValidator.validarQuilometragem(novaQuilometragem);

        if (novaQuilometragem < veiculo.getQuilometragem()) {
            System.out.println("AVISO: Quilometragem menor que a atual!"); // FIXME: Use logger
        }

        veiculo.setQuilometragem(novaQuilometragem);
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo atualizado = veiculoRepository.save(veiculo);

        // Verificar se precisa de manutenção preventiva
        if (manutencaoPreventivaService.precisaDeManutencao(veiculoId)) {
            veiculoNotificador.notificar("ALERTA_MANUTENCAO",
                "Veículo " + veiculo.getPlaca() + " atingiu " + novaQuilometragem + " km. Agendar manutenção preventiva.");
        }

        return atualizado;
    }

    /**
     * Obtém veículos por status
     *
     * FIXME: No pagination
     */
    public List<Veiculo> obterVeiculosPorStatus(String status) {
        veiculoValidator.validarStatus(status);

        return veiculoRepository.findByStatus(status);
    }

    /**
     * Desativa veículo
     *
     * FIXME: No soft delete
     * FIXME: No cascade handling
     */
    public Veiculo desativarVeiculo(Long veiculoId) {
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus("INATIVO");
        veiculo.setDataAtualizacao(LocalDateTime.now());

        Veiculo desativado = veiculoRepository.save(veiculo);
        log.info("Veículo desativado: {}", veiculo.getPlaca());

        veiculoNotificador.notificar("VEICULO_DESATIVADO",
            "Veículo " + veiculo.getPlaca() + " foi desativado");

        return desativado;
    }

    /**
     * Reativa veículo
     */
    public Veiculo reativarVeiculo(Long veiculoId) {
        Veiculo veiculo = buscarPorId(veiculoId);
        veiculo.setStatus("ATIVO");
        veiculo.setDataAtualizacao(LocalDateTime.now());

        log.info("Veículo reativado: {}", veiculo.getPlaca());
        return veiculoRepository.save(veiculo);
    }

    /**
     * Obtém estatísticas de frota
     *
     * FIXME: Typo in method name (Freita instead of Frota)
     * FIXME: No proper response object
     */
    public String obterEstatisticasFreita() {
        // FIXME: Typo no nome do método nunca foi corrigido
        long totalVeiculos = veiculoRepository.count();
        long ativos = veiculoRepository.findByStatus("ATIVO").size(); // FIXME: Deveria ser count query
        long inativos = veiculoRepository.findByStatus("INATIVO").size();
        long emManutencao = veiculoRepository.findByStatus("MANUTENCAO").size();

        // FIXME: Retornando JSON como String em vez de usar DTO
        return String.format(
            "{\"total\": %d, \"ativos\": %d, \"inativos\": %d, \"em_manutencao\": %d}",
            totalVeiculos, ativos, inativos, emManutencao
        );
    }

    /**
     * Sincroniza dados com sistema externo
     *
     * FIXME: No retry logic
     * FIXME: No circuit breaker
     */
    public void sincronizarComSistemaExterno() {
        // FIXME: URL hardcoded
        log.info("Sincronização com sistema externo iniciada");
        System.out.println("Sincronização iniciada"); // FIXME: Use logger

        // TODO: Implementar integração real
        // TODO: Adicionar retry logic
        // TODO: Adicionar circuit breaker
    }

    // TODO: Add proper exception handling
    // TODO: Add metrics
    // TODO: Add caching
    // TODO: Add authorization checks
    // TODO: Add audit trail
}
