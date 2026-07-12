package com.deploypilot.service;

import com.deploypilot.dto.*;
import com.deploypilot.model.EnvVarDefinition;
import com.deploypilot.model.ProjectEnvVar;
import com.deploypilot.model.enums.EnvVarClassification;
import com.deploypilot.repository.EnvVarDefinitionRepository;
import com.deploypilot.repository.ProjectEnvVarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EnvVarService {
    private final EnvVarDefinitionRepository defRepo;
    private final ProjectEnvVarRepository varRepo;
    public EnvVarService(EnvVarDefinitionRepository defRepo, ProjectEnvVarRepository varRepo) {
        this.defRepo = defRepo; this.varRepo = varRepo;
    }

    @Transactional(readOnly = true)
    public List<EnvVarDefinitionResponse> getDefinitions() {
        return defRepo.findAll().stream().map(this::toDefResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EnvVarResponse> getProjectEnvVars(Long projectId) {
        return varRepo.findByProjectId(projectId).stream().map(this::toVarResponse).collect(Collectors.toList());
    }

    @Transactional
    public EnvVarResponse createProjectEnvVar(Long projectId, EnvVarCreateRequest req) {
        ProjectEnvVar v = new ProjectEnvVar();
        v.setProjectId(projectId);
        v.setVariableName(req.getVariableName());
        v.setDescription(req.getDescription());
        v.setClassification(req.getClassification());
        v.setLocalLocation(req.getLocalLocation());
        v.setProductionLocation(req.getProductionLocation());
        v.setRequired(req.isRequired());
        v.setConfigured(false);
        return toVarResponse(varRepo.save(v));
    }

    @Transactional
    public EnvVarResponse updateEnvVar(Long varId, EnvVarUpdateRequest req) {
        ProjectEnvVar v = varRepo.findById(varId).orElseThrow(() -> new com.deploypilot.exception.ResourceNotFoundException("Variable not found"));
        v.setConfigured(req.isConfigured());
        v.setNotes(req.getNotes());
        return toVarResponse(varRepo.save(v));
    }

    @Transactional
    public void deleteEnvVar(Long varId) { varRepo.deleteById(varId); }

    private EnvVarDefinitionResponse toDefResponse(EnvVarDefinition d) {
        EnvVarDefinitionResponse r = new EnvVarDefinitionResponse();
        r.setId(d.getId()); r.setName(d.getName()); r.setDescription(d.getDescription());
        r.setCategory(d.getCategory()); r.setPlatform(d.getPlatform());
        r.setLocalFileLocation(d.getLocalFileLocation()); r.setProductionLocation(d.getProductionLocation());
        r.setRequired(d.isRequired()); r.setExampleValue(d.getExampleValue());
        return r;
    }

    private EnvVarResponse toVarResponse(ProjectEnvVar v) {
        EnvVarResponse r = new EnvVarResponse();
        r.setId(v.getId()); r.setVariableName(v.getVariableName()); r.setDescription(v.getDescription());
        r.setClassification(v.getClassification()); r.setLocalLocation(v.getLocalLocation());
        r.setProductionLocation(v.getProductionLocation()); r.setRequired(v.isRequired());
        r.setConfigured(v.isConfigured()); r.setLastVerifiedAt(v.getLastVerifiedAt()); r.setNotes(v.getNotes());
        return r;
    }
}
