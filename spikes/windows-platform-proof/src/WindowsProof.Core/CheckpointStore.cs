using System.Text.Json;

namespace MediaEcosystem.WindowsProof;

public sealed class CheckpointLoadResult
{
    public ProofSessionState State { get; init; } = ProofSessionState.CreateNew();
    public bool Found { get; init; }
    public bool RecoveredFromPrevious { get; init; }
    public string? Failure { get; init; }
}

public sealed class CheckpointStore
{
    private readonly SemaphoreSlim gate = new(1, 1);
    private readonly string root;
    private readonly string primary;
    private readonly string previous;
    private readonly string temporary;
    private readonly JsonSerializerOptions jsonOptions = JsonDefaults.Create(indented: true);

    public CheckpointStore(string privateRoot)
    {
        ArgumentException.ThrowIfNullOrWhiteSpace(privateRoot);
        root = Path.GetFullPath(privateRoot);
        primary = Path.Combine(root, "proof-checkpoint.json");
        previous = Path.Combine(root, "proof-checkpoint.previous.json");
        temporary = Path.Combine(root, "proof-checkpoint.partial");
    }

    public async Task SaveAsync(
        ProofSessionState state,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(state);
        await gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            Directory.CreateDirectory(root);
            state.LastUpdatedUtc = DateTimeOffset.UtcNow;
            byte[] bytes = JsonSerializer.SerializeToUtf8Bytes(state, jsonOptions);
            await using (FileStream stream = new(
                temporary,
                FileMode.Create,
                FileAccess.Write,
                FileShare.None,
                bufferSize: 64 * 1024,
                FileOptions.Asynchronous | FileOptions.WriteThrough))
            {
                await stream.WriteAsync(bytes, cancellationToken).ConfigureAwait(false);
                await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
            }

            if (File.Exists(primary))
            {
                File.Copy(primary, previous, overwrite: true);
            }

            File.Move(temporary, primary, overwrite: true);
        }
        finally
        {
            gate.Release();
        }
    }

    public async Task<CheckpointLoadResult> LoadAsync(
        CancellationToken cancellationToken = default)
    {
        await gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!File.Exists(primary))
            {
                return new CheckpointLoadResult();
            }

            try
            {
                ProofSessionState state = await ReadAsync(primary, cancellationToken)
                    .ConfigureAwait(false);
                state.LoadedFromPrivateCheckpoint = true;
                return new CheckpointLoadResult { State = state, Found = true };
            }
            catch (Exception exception) when (
                exception is JsonException or IOException or UnauthorizedAccessException)
            {
                if (File.Exists(previous))
                {
                    try
                    {
                        ProofSessionState state = await ReadAsync(previous, cancellationToken)
                            .ConfigureAwait(false);
                        state.LoadedFromPrivateCheckpoint = true;
                        state.Failures.Add("checkpoint_primary_invalid_recovered_previous");
                        return new CheckpointLoadResult
                        {
                            State = state,
                            Found = true,
                            RecoveredFromPrevious = true,
                            Failure = "primary_checkpoint_invalid",
                        };
                    }
                    catch (Exception previousException) when (
                        previousException is JsonException or IOException or
                        UnauthorizedAccessException)
                    {
                        return new CheckpointLoadResult
                        {
                            Failure = "primary_and_previous_checkpoint_invalid",
                        };
                    }
                }

                return new CheckpointLoadResult { Failure = "primary_checkpoint_invalid" };
            }
        }
        finally
        {
            gate.Release();
        }
    }

    private async Task<ProofSessionState> ReadAsync(
        string path,
        CancellationToken cancellationToken)
    {
        await using FileStream stream = new(
            path,
            FileMode.Open,
            FileAccess.Read,
            FileShare.Read,
            bufferSize: 64 * 1024,
            FileOptions.Asynchronous | FileOptions.SequentialScan);
        ProofSessionState? state = await JsonSerializer.DeserializeAsync<ProofSessionState>(
            stream,
            jsonOptions,
            cancellationToken).ConfigureAwait(false);
        if (state is null ||
            !string.Equals(state.StateSchemaVersion, "1.0.0", StringComparison.Ordinal))
        {
            throw new JsonException("Unsupported or empty checkpoint.");
        }

        return state;
    }
}
