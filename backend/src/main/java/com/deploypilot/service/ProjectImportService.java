package com.deploypilot.service;

import com.deploypilot.dto.ImportRepositoryResponse;
import com.deploypilot.dto.RepositoryAnalysisResponse;
import com.deploypilot.model.Project;
import com.deploypilot.model.enums.ProjectStatus;
import com.deploypilot.repoaccess.RepositoryFileReader;
import com.deploypilot.repoaccess.RepositoryRef;
import com.deploypilot.repository.ProjectRepository;
import com.deploypilot.util.CurrentUserUtil;
import org.springframework.stereotype.Service;

/**
 * "Import from GitHub" flow: validates the repository first (so no project is
 * created for a repo that does not exist or is not accessible), then creates
 * the project, persists a full analysis and generates the blueprint.
 */
@Service
public class ProjectImportService {

    private final ProjectRepository projectRepository;
    private final RepositoryAnalysisService analysisService;
    private final DeploymentBlueprintService blueprintService;
    private final RepositoryFileReader fileReader;

    public ProjectImportService(ProjectRepository projectRepository,
                                RepositoryAnalysisService analysisService,
                                DeploymentBlueprintService blueprintService,
                                RepositoryFileReader fileReader) {
        this.projectRepository = projectRepository;
        this.analysisService = analysisService;
        this.blueprintService = blueprintService;
        this.fileReader = fileReader;
    }

    public ImportRepositoryResponse importRepository(String repositoryInput) {
        RepositoryRef ref = RepositoryRef.parse(repositoryInput);
        // Validate accessibility before creating anything (throws clear errors)
        fileReader.fetchMetadata(ref);

        Project project = new Project();
        project.setName(ref.name());
        project.setDescription("Imported from GitHub (" + ref.fullName() + ")");
        project.setGithubUrl("https://github.com/" + ref.fullName());
        project.setStatus(ProjectStatus.PLANNING);
        project.setUserId(CurrentUserUtil.getCurrentUserId());
        project = projectRepository.save(project);

        RepositoryAnalysisResponse analysis = analysisService.analyze(project.getId(), ref.fullName());

        ImportRepositoryResponse response = new ImportRepositoryResponse();
        response.setProjectId(project.getId());
        response.setProjectName(project.getName());
        response.setAnalysis(analysis);
        response.setBlueprint(blueprintService.generate(project.getId()));
        return response;
    }
}
