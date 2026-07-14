import { Routes, Route } from 'react-router-dom';
import { useEffect } from 'react';
import { useAuth } from '@/hooks/useAuth';
import Layout from '@/components/Layout';
import ProtectedRoute from '@/components/ProtectedRoute';
import LandingPage from '@/pages/LandingPage';
import LoginPage from '@/pages/LoginPage';
import RegisterPage from '@/pages/RegisterPage';
import DashboardPage from '@/pages/DashboardPage';
import ProjectsPage from '@/pages/ProjectsPage';
import ProjectWizardPage from '@/pages/ProjectWizardPage';
import DeploymentPlanPage from '@/pages/DeploymentPlanPage';
import RepositoryAnalysisPage from '@/pages/RepositoryAnalysisPage';
import GitCommandsPage from '@/pages/GitCommandsPage';
import EnvVarsPage from '@/pages/EnvVarsPage';
import GuidesPage from '@/pages/GuidesPage';
import GuideDetailPage from '@/pages/GuideDetailPage';
import TroubleshootPage from '@/pages/TroubleshootPage';
import SecurityPage from '@/pages/SecurityPage';
import GlossaryPage from '@/pages/GlossaryPage';
import AboutDeploymentPage from '@/pages/AboutDeploymentPage';

export default function App() {
  const { refetch } = useAuth();
  useEffect(() => { refetch(); }, [refetch]);

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<LandingPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/glossary" element={<GlossaryPage />} />
        <Route path="/about-deployment" element={<AboutDeploymentPage />} />
        <Route path="/guides/:slug" element={<GuideDetailPage />} />
        <Route element={<ProtectedRoute />}>
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/projects/new" element={<ProjectWizardPage />} />
          <Route path="/projects/:id/plan" element={<DeploymentPlanPage />} />
          <Route path="/projects/:id/analysis" element={<RepositoryAnalysisPage />} />
          <Route path="/git-commands" element={<GitCommandsPage />} />
          <Route path="/env-vars" element={<EnvVarsPage />} />
          <Route path="/guides" element={<GuidesPage />} />
          <Route path="/troubleshoot" element={<TroubleshootPage />} />
          <Route path="/security" element={<SecurityPage />} />
        </Route>
      </Route>
    </Routes>
  );
}
