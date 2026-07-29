namespace MediaEcosystem.WindowsProof;

internal sealed class SessionController
{
    private readonly CheckpointStore store = new(ProofRuntime.PrivateCheckpointRoot);

    public ProofSessionState State { get; private set; } = ProofSessionState.CreateNew();

    public async Task<CheckpointLoadResult> LoadAsync()
    {
        CheckpointLoadResult loaded = await store.LoadAsync();
        State = loaded.State;
        if (loaded.Found &&
            State.Lifecycle.RestartCheckpoint.Status == "awaiting_reopen")
        {
            RestartCheckpoint checkpoint = State.Lifecycle.RestartCheckpoint;
            checkpoint.Status = "reopened";
            checkpoint.CleanReopenObserved = true;
            checkpoint.ReopenedUtc = DateTimeOffset.UtcNow;
            AddDiagnostic("restart_checkpoint", "reopened", "private_state_recovered");
            await SaveAsync();
        }
        else if (loaded.Failure is not null)
        {
            State.Failures.Add(loaded.Failure);
            AddDiagnostic("checkpoint_load", "failed", loaded.Failure);
        }

        return loaded;
    }

    public void AddDiagnostic(string eventName, string status, string details)
    {
        PrivacyValidator.ValidateText(details);
        State.DiagnosticLog.Add(new DiagnosticEntry
        {
            Utc = DateTimeOffset.UtcNow,
            MonotonicMs = Environment.TickCount64,
            Event = eventName,
            Status = status,
            Details = details,
        });
    }

    public Task SaveAsync()
    {
        State.Lifecycle.Disposition = ProofAggregators.EvaluateLifecycle(State.Lifecycle);
        foreach (FormatResult result in State.FormatResults)
        {
            result.Disposition = ProofAggregators.EvaluateFormat(result);
        }

        return store.SaveAsync(State);
    }

    public async Task BeginRestartCheckpointAsync()
    {
        RestartCheckpoint checkpoint = State.Lifecycle.RestartCheckpoint;
        checkpoint.Status = "awaiting_reopen";
        checkpoint.Generation++;
        checkpoint.BeganUtc = DateTimeOffset.UtcNow;
        checkpoint.ReopenedUtc = null;
        checkpoint.CompletedUtc = null;
        checkpoint.CleanReopenObserved = false;
        checkpoint.NewPlaybackStartedAfterReopen = false;
        checkpoint.AbsolutePathExported = false;
        AddDiagnostic("restart_checkpoint", "awaiting_reopen", "user_must_close_and_reopen");
        await SaveAsync();
    }

    public async Task CompleteRestartCheckpointAsync()
    {
        RestartCheckpoint checkpoint = State.Lifecycle.RestartCheckpoint;
        if (checkpoint.Status != "reopened" ||
            !checkpoint.CleanReopenObserved ||
            !checkpoint.NewPlaybackStartedAfterReopen)
        {
            throw new InvalidOperationException(
                "Restart completion requires a clean reopen and new playback start.");
        }

        checkpoint.Status = "completed";
        checkpoint.CompletedUtc = DateTimeOffset.UtcNow;
        AddDiagnostic("restart_checkpoint", "completed", "clean_reopen_and_new_playback");
        await SaveAsync();
    }
}
