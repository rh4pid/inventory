interface Props {
  title: string;
  description?: string;
  action?: React.ReactNode;
  /** The table/grid switch, shown before the actions. */
  view?: React.ReactNode;
}

export function PageHeader({ title, description, action, view }: Props) {
  return (
    <div className="mb-6 flex flex-wrap items-start justify-between gap-3">
      <div>
        <h1 className="text-[26px] font-semibold tracking-tight text-ink md:text-[28px]">{title}</h1>
        {description && <p className="mt-1 text-sm text-ink-3">{description}</p>}
      </div>
      {(action || view) && (
        <div className="flex flex-wrap items-center gap-2">
          {view}
          {action && <div>{action}</div>}
        </div>
      )}
    </div>
  );
}
