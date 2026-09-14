package com.medtracker.medtracker.service;

import com.medtracker.medtracker.model.Medicamento;
import com.medtracker.medtracker.repository.MedicamentoRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
public class MedicamentoService {

    private final MedicamentoRepository medicamentoRepository;

    public MedicamentoService(MedicamentoRepository medicamentoRepository) {
        this.medicamentoRepository = medicamentoRepository;
    }

    // Registrar un nuevo medicamento
    public Medicamento registrarMedicamento(Medicamento medicamento) {
        if (medicamentoRepository.existsByNombre(medicamento.getNombre())) {
            throw new IllegalArgumentException("El medicamento ya está registrado");
        }
        return medicamentoRepository.save(medicamento);
    }

    // Buscar medicamento por nombre
    public Optional<Medicamento> buscarPorNombre(String nombre) {
        return medicamentoRepository.findByNombre(nombre);
    }

    // Buscar medicamento por ID
    public Optional<Medicamento> buscarPorId(Long id) {
        return medicamentoRepository.findById(id);
    }

    // Listar todos los medicamentos
    public List<Medicamento> listarTodos() {
        return medicamentoRepository.findAll();
    }
}
