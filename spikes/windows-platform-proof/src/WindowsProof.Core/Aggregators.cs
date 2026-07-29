namespace MediaEcosystem.WindowsProof;

public static class ProofAggregators
{
    public static ProofDisposition EvaluateFormat(FormatResult result)
    {
        if (!result.OpenAttempted)
        {
            return ProofDisposition.NotRun;
        }

        bool passed =
            result.SourceAccepted &&
            result.MediaOpenedPrepared &&
            result.PlaybackStarted &&
            result.PositionAdvanced &&
            result.SeekRequested &&
            result.SeekCompleted &&
            result.DurationWithinTolerance &&
            result.EndOfTrackObserved;
        if (passed)
        {
            return ProofDisposition.Passed;
        }

        return result.Timeouts.Count > 0 ? ProofDisposition.Inconclusive : ProofDisposition.Failed;
    }

    public static ProofDisposition EvaluatePb01(IReadOnlyCollection<FormatResult> results)
    {
        ILookup<string, FormatResult> grouped = results.ToLookup(
            item => item.FixtureId,
            StringComparer.Ordinal);
        if (grouped.Any(group => group.Count() != 1))
        {
            return ProofDisposition.Inconclusive;
        }

        Dictionary<string, FormatResult> byId = grouped.ToDictionary(
            group => group.Key,
            group => group.Single(),
            StringComparer.Ordinal);
        if (byId.Count != ProofConstants.RequiredFixtureIds.Count ||
            byId.Keys.Except(ProofConstants.RequiredFixtureIds, StringComparer.Ordinal).Any() ||
            ProofConstants.RequiredFixtureIds.Any(id => !byId.ContainsKey(id)))
        {
            return results.Count == 0 ? ProofDisposition.NotRun : ProofDisposition.Inconclusive;
        }

        foreach (FormatResult result in byId.Values)
        {
            result.Disposition = EvaluateFormat(result);
        }

        if (byId.Values.All(item => item.Disposition == ProofDisposition.Passed))
        {
            return ProofDisposition.Passed;
        }

        return byId.Values.Any(item => item.Disposition == ProofDisposition.Failed)
            ? ProofDisposition.Failed
            : ProofDisposition.Inconclusive;
    }

    public static ProofDisposition EvaluateLifecycle(LifecycleResult result)
    {
        bool systemPlay = result.SystemCommands.Any(item =>
            item.Command == "play" &&
            item.AutomaticallyObservedByCommandManager &&
            item.AffectedPlayback);
        bool systemPause = result.SystemCommands.Any(item =>
            item.Command == "pause" &&
            item.AutomaticallyObservedByCommandManager &&
            item.AffectedPlayback);
        bool metadataVisible = result.ManualAcknowledgements.Any(item =>
            item.ObservationId == "smtc_metadata_visible" &&
            item.ObservationSource == "human" &&
            item.Acknowledged);
        bool restartPassed =
            result.RestartCheckpoint.Status == "completed" &&
            result.RestartCheckpoint.CleanReopenObserved &&
            result.RestartCheckpoint.NewPlaybackStartedAfterReopen &&
            !result.RestartCheckpoint.AbsolutePathExported;

        if (!result.PlaybackBegan && !result.ReadyForSleepMarked &&
            result.SystemCommands.Count == 0 && result.ManualAcknowledgements.Count == 0)
        {
            return ProofDisposition.NotRun;
        }

        bool passed =
            result.FixtureVerified &&
            result.PlaybackBegan &&
            result.AutomaticSmtcCandidateActive &&
            metadataVisible &&
            systemPlay &&
            systemPause &&
            result.ReadyForSleepMarked &&
            !string.IsNullOrWhiteSpace(result.PlaybackStateImmediatelyBeforeSleep) &&
            result.SuspendObserved &&
            result.ResumeObserved &&
            result.WakeResultRecorded &&
            !string.IsNullOrWhiteSpace(result.PlaybackStateAfterWake) &&
            result.AutomaticSmtcCandidateActiveAfterWake &&
            restartPassed;

        if (passed)
        {
            return ProofDisposition.Passed;
        }

        return result.Failures.Count > 0
            ? ProofDisposition.Failed
            : ProofDisposition.Inconclusive;
    }
}
