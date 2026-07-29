namespace MediaEcosystem.WindowsProof;

public static class MatrixCoordinator
{
    public static async Task<IReadOnlyList<FormatResult>> RunAllAsync(
        IReadOnlyList<VerifiedFixture> fixtures,
        Func<VerifiedFixture, CancellationToken, Task<FormatResult>> runOne,
        Action<FormatResult>? completed,
        CancellationToken cancellationToken)
    {
        ArgumentNullException.ThrowIfNull(fixtures);
        ArgumentNullException.ThrowIfNull(runOne);

        Dictionary<string, VerifiedFixture> byId = fixtures.ToDictionary(
            item => item.Id,
            StringComparer.Ordinal);
        if (byId.Count != ProofConstants.RequiredFixtureIds.Count ||
            ProofConstants.RequiredFixtureIds.Any(id => !byId.ContainsKey(id)))
        {
            throw new InvalidOperationException(
                "The verified fixture set does not match the exact six-format contract.");
        }

        List<FormatResult> results = [];
        foreach (string id in ProofConstants.RequiredFixtureIds)
        {
            VerifiedFixture fixture = byId[id];
            FormatResult result;
            try
            {
                result = await runOne(fixture, cancellationToken).ConfigureAwait(false);
                if (!string.Equals(result.FixtureId, fixture.Id, StringComparison.Ordinal))
                {
                    throw new InvalidOperationException("Runner returned the wrong fixture identity.");
                }
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                result = FailedResult(fixture, "matrix_cancelled", inconclusive: true);
            }
            catch (Exception exception)
            {
                result = FailedResult(
                    fixture,
                    $"decoder_runner_exception:{exception.GetType().Name}",
                    inconclusive: false);
            }

            result.Disposition = ProofAggregators.EvaluateFormat(result);
            results.Add(result);
            completed?.Invoke(result);

            if (cancellationToken.IsCancellationRequested)
            {
                foreach (string remainingId in ProofConstants.RequiredFixtureIds.Skip(results.Count))
                {
                    VerifiedFixture remaining = byId[remainingId];
                    FormatResult notRun = new()
                    {
                        FixtureId = remaining.Id,
                        VerifiedFilename = remaining.Filename,
                        VerifiedSizeBytes = remaining.SizeBytes,
                        VerifiedSha256 = remaining.Sha256,
                        ExpectedDurationMs = remaining.ExpectedDurationMs,
                        DurationToleranceMs = remaining.DurationToleranceMs,
                        Disposition = ProofDisposition.NotRun,
                        Warnings = ["matrix_cancelled_before_fixture"],
                    };
                    results.Add(notRun);
                    completed?.Invoke(notRun);
                }

                break;
            }
        }

        return results;
    }

    private static FormatResult FailedResult(
        VerifiedFixture fixture,
        string error,
        bool inconclusive)
    {
        FormatResult result = new()
        {
            FixtureId = fixture.Id,
            VerifiedFilename = fixture.Filename,
            VerifiedSizeBytes = fixture.SizeBytes,
            VerifiedSha256 = fixture.Sha256,
            ExpectedDurationMs = fixture.ExpectedDurationMs,
            DurationToleranceMs = fixture.DurationToleranceMs,
            OpenAttempted = true,
        };
        if (inconclusive)
        {
            result.Timeouts.Add(error);
        }
        else
        {
            result.Errors.Add(error);
        }

        return result;
    }
}
