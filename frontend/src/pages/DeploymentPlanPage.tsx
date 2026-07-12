import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { planApi } from '@/lib/api';
import type { DeploymentStep } from '@/types';
import TerminalBlock from '@/components/TerminalBlock';
import StatusBadge from '@/components/StatusBadge';
import ProgressBar from '@/components/ProgressBar';
import {
  CheckCircle, XCircle, SkipForward, RotateCcw, Bookmark,
  ChevronDown, ChevronUp, AlertTriangle, ArrowRight, MapPin
} from 'lucide-react';

export default function DeploymentPlanPage() {
  const { id } = useParams<{ id: string }>();
  const projectId = Number(id);
  const qc = useQueryClient();
  const [expandedStep, setExpandedStep] = useState<number | null>(0);
  const [beginnerMode, setBeginnerMode] = useState(true);

  const { data: plan, isLoading } = useQuery({
    queryKey: ['plan', projectId],
    queryFn: () => planApi.get(projectId),
  });

  const updateMut = useMutation({
    mutationFn: ({ stepIndex, status, note }: { stepIndex: number; status: string; note?: string }) =>
      planApi.updateStep(projectId, stepIndex, { status, note }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['plan', projectId] }),
  });

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-slate-200 dark:bg-slate-700 rounded w-1/3" />
          <div className="h-4 bg-slate-200 dark:bg-slate-700 rounded w-1/2" />
          <div className="h-32 bg-slate-200 dark:bg-slate-700 rounded" />
        </div>
      </div>
    );
  }

  if (!plan) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 py-16 text-center">
        <h2 className="text-xl font-bold mb-2">No deployment plan yet</h2>
        <p className="text-slate-500">Go through the project wizard to generate a personalized deployment plan.</p>
      </div>
    );
  }

  const steps = plan.steps || [];
  const currentIdx = plan.currentStepIndex || 0;
  const completed = plan.completedSteps || 0;
  const total = plan.totalSteps || steps.length;

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 py-8">
      {/* Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-6">
        <div>
          <h1 className="text-2xl font-bold">Deployment Plan</h1>
          <p className="text-slate-500 text-sm mt-0.5">{completed} of {total} steps completed</p>
        </div>
        <div className="flex items-center gap-3">
          <button onClick={() => setBeginnerMode(!beginnerMode)}
            className="btn-secondary text-sm">
            {beginnerMode ? 'Advanced Mode' : 'Beginner Mode'}
          </button>
        </div>
      </div>

      {/* Progress */}
      <div className="card p-4 mb-6">
        <ProgressBar current={completed} total={total} />
      </div>

      {/* Current step highlight */}
      {currentIdx < steps.length && (
        <div className="bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800 rounded-xl p-4 mb-6 flex items-center gap-3">
          <ArrowRight className="w-5 h-5 text-primary-600 shrink-0" />
          <div>
            <p className="text-sm text-primary-700 dark:text-primary-400 font-medium">Current Step: {steps[currentIdx]?.title}</p>
            <p className="text-xs text-primary-600/70 mt-0.5">{steps[currentIdx]?.whatToDo}</p>
          </div>
        </div>
      )}

      {/* Steps */}
      <div className="space-y-3">
        {steps.map((step: DeploymentStep, idx: number) => {
          const expanded = expandedStep === idx;
          return (
            <div key={idx}
              className={`card overflow-hidden transition-all ${
                step.status === 'COMPLETED' ? 'border-green-300 dark:border-green-800' :
                step.status === 'BLOCKED' ? 'border-red-300 dark:border-red-800' :
                idx === currentIdx ? 'border-primary-300 dark:border-primary-700 ring-1 ring-primary-200' : ''
              }`}>
              {/* Step header */}
              <button onClick={() => setExpandedStep(expanded ? null : idx)}
                className="w-full flex items-center gap-3 p-4 text-left hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold shrink-0 ${
                  step.status === 'COMPLETED' ? 'bg-green-500 text-white' :
                  step.status === 'BLOCKED' ? 'bg-red-500 text-white' :
                  step.status === 'SKIPPED' ? 'bg-slate-400 text-white' :
                  idx === currentIdx ? 'bg-primary-600 text-white' : 'bg-slate-200 dark:bg-slate-700 text-slate-600'
                }`}>
                  {step.status === 'COMPLETED' ? <CheckCircle className="w-4 h-4" /> :
                   step.status === 'BLOCKED' ? <XCircle className="w-4 h-4" /> : idx + 1}
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-sm">{step.title}</div>
                  <div className="text-xs text-slate-500 line-clamp-1">{step.description}</div>
                </div>
                <StatusBadge status={step.status || 'NOT_STARTED'} />
                {expanded ? <ChevronUp className="w-4 h-4 text-slate-400" /> : <ChevronDown className="w-4 h-4 text-slate-400" />}
              </button>

              {/* Expanded content */}
              {expanded && (
                <div className="px-4 pb-4 border-t border-slate-100 dark:border-slate-700/50 pt-4 space-y-4">
                  {/* What to do */}
                  <div>
                    <h4 className="text-sm font-semibold flex items-center gap-1.5 mb-1.5">
                      <MapPin className="w-4 h-4 text-primary-600" />What to do
                    </h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400">{step.whatToDo}</p>
                  </div>

                  {/* Why */}
                  {beginnerMode && step.whyNecessary && (
                    <div className="bg-blue-50 dark:bg-blue-900/10 rounded-lg p-3">
                      <h4 className="text-sm font-semibold text-blue-800 dark:text-blue-400 mb-1">Why this is necessary</h4>
                      <p className="text-sm text-blue-700 dark:text-blue-300">{step.whyNecessary}</p>
                    </div>
                  )}

                  {/* Where */}
                  <div>
                    <h4 className="text-sm font-semibold mb-1">Where to do it</h4>
                    <p className="text-sm text-slate-600 dark:text-slate-400">{step.whereToDoIt}</p>
                  </div>

                  {/* Command */}
                  {step.commandOrValue && (
                    <div>
                      <h4 className="text-sm font-semibold mb-2">Command or value</h4>
                      <TerminalBlock command={step.commandOrValue} explanation={step.whatCommandDoes || undefined} />
                    </div>
                  )}

                  {/* Expected result */}
                  {step.expectedResult && (
                    <div>
                      <h4 className="text-sm font-semibold mb-1">Expected result</h4>
                      <p className="text-sm text-slate-600 dark:text-slate-400">{step.expectedResult}</p>
                    </div>
                  )}

                  {/* Common errors */}
                  {step.commonErrors && step.commonErrors.length > 0 && (
                    <div className="bg-amber-50 dark:bg-amber-900/10 rounded-lg p-3">
                      <h4 className="text-sm font-semibold text-amber-800 dark:text-amber-400 mb-2">Common errors</h4>
                      <ul className="space-y-1">
                        {step.commonErrors.map((err, i) => (
                          <li key={i} className="text-sm text-amber-700 dark:text-amber-300 flex items-start gap-2">
                            <span className="text-amber-500 mt-0.5">-</span>{err}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Security warning */}
                  {step.securityWarning && (
                    <div className="bg-red-50 dark:bg-red-900/10 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-start gap-2">
                      <AlertTriangle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
                      <div>
                        <h4 className="text-sm font-semibold text-red-800 dark:text-red-400">Security Warning</h4>
                        <p className="text-sm text-red-700 dark:text-red-300">{step.securityWarning}</p>
                      </div>
                    </div>
                  )}

                  {/* Actions */}
                  <div className="flex flex-wrap gap-2 pt-2">
                    {step.status !== 'COMPLETED' && (
                      <button onClick={() => updateMut.mutate({ stepIndex: idx, status: 'COMPLETED' })}
                        className="btn-primary text-sm py-1.5">
                        <CheckCircle className="w-4 h-4" />Mark Complete
                      </button>
                    )}
                    {step.status !== 'BLOCKED' && (
                      <button onClick={() => updateMut.mutate({ stepIndex: idx, status: 'BLOCKED' })}
                        className="btn-danger text-sm py-1.5">
                        <XCircle className="w-4 h-4" />Blocked
                      </button>
                    )}
                    {step.status !== 'SKIPPED' && step.status !== 'COMPLETED' && (
                      <button onClick={() => updateMut.mutate({ stepIndex: idx, status: 'SKIPPED' })}
                        className="btn-secondary text-sm py-1.5">
                        <SkipForward className="w-4 h-4" />Skip
                      </button>
                    )}
                    {(step.status === 'COMPLETED' || step.status === 'BLOCKED' || step.status === 'SKIPPED') && (
                      <button onClick={() => updateMut.mutate({ stepIndex: idx, status: 'NOT_STARTED' })}
                        className="btn-secondary text-sm py-1.5">
                        <RotateCcw className="w-4 h-4" />Reset
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
