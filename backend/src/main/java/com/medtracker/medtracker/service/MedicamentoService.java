package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medicamento;
import com.medtracker.medtracker.repository.MedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicamentoService {

    private final MedicamentoRepository medicamentoRepository;

    // Registrar un nuevo medicamento
    public Medicamento registrarMedicamento(Medicamento medicamento) {
        return medicamentoRepository.save(medicamento);
    }

    // Buscar medicamento por ID
    public Optional<Medicamento> buscarPorId(Long id) {
        return medicamentoRepository.findById(id);
    }

    // Listar todos los medicamentos
    public List<Medicamento> listarTodos() {
        return medicamentoRepository.findAll();
    }

    // Buscar medicamento por nombre
    public Optional<Medicamento> buscarPorNombre(String nombre) {
        return medicamentoRepository.findByNombre(nombre);
    }
}
