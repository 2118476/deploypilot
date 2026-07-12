interface Props {
  status: string;
  label?: string;
}

const map: Record<string, { cls: string; text: string }> = {
  COMPLETED: { cls: 'badge-green', text: 'Complete' },
  ACTIVE: { cls: 'badge-blue', text: 'Active' },
  IN_PROGRESS: { cls: 'badge-blue', text: 'In Progress' },
  PENDING: { cls: 'badge-amber', text: 'Pending' },
  BLOCKED: { cls: 'badge-red', text: 'Blocked' },
  SKIPPED: { cls: 'badge-gray', text: 'Skipped' },
  NOT_STARTED: { cls: 'badge-gray', text: 'Not Started' },
  PLANNING: { cls: 'badge-amber', text: 'Planning' },
  DEPLOYED: { cls: 'badge-green', text: 'Deployed' },
  ARCHIVED: { cls: 'badge-gray', text: 'Archived' },
  SUCCESS: { cls: 'badge-green', text: 'Success' },
  FAILED: { cls: 'badge-red', text: 'Failed' },
  PUBLIC: { cls: 'badge-blue', text: 'Public' },
  SECRET: { cls: 'badge-red', text: 'Secret' },
};

export default function StatusBadge({ status, label }: Props) {
  const m = map[status] || { cls: 'badge-gray', text: status };
  return <span className={m.cls}>{label || m.text}</span>;
}
