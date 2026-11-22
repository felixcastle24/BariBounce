<script lang="ts">
    import {onMount} from "svelte";
    import {fly} from "svelte/transition";
    import {
        browseLocalConfigs,
        getLocalConfigs,
        loadLocalConfig,
        type LocalConfig,
        saveLocalConfig
    } from "../../../integration/rest";

    let {onLoaded, onListChanged}: {
        onLoaded: () => void;
        onListChanged?: (count: number, active: string) => void;
    } = $props();

    let configs = $state<LocalConfig[]>([]);
    let active = $state("");
    let loaded = $state(false);
    let newName = $state("");
    let busy = $state<string | null>(null);
    // Name of the config an overwrite was asked for, waiting for a second click
    let confirmOverwrite = $state<string | null>(null);
    let message = $state<{ text: string; error: boolean } | null>(null);
    let messageTimeout: number | undefined;

    const INVALID_NAME = /[\\/:*?"<>|]|\.\./;
    const trimmedName = $derived(newName.trim());
    const nameError = $derived(
        trimmedName && INVALID_NAME.test(trimmedName) ? "Names can't contain \\ / : * ? \" < > | or .." : null
    );

    function notify(text: string, error = false) {
        message = {text, error};
        clearTimeout(messageTimeout);
        messageTimeout = setTimeout(() => (message = null), 3000);
    }

    async function refresh() {
        try {
            const list = await getLocalConfigs();
            configs = list.configs;
            active = list.active;
            onListChanged?.(configs.length, active);
        } catch {
            notify("Could not read the configs folder", true);
        }
        loaded = true;
    }

    async function load(name: string) {
        busy = name;
        try {
            await loadLocalConfig(name);
            notify(`Loaded "${name}"`);
            await refresh();
            onLoaded();
        } catch (e) {
            notify((e as Error).message, true);
        } finally {
            busy = null;
        }
    }

    async function save(name: string, overwrite: boolean) {
        if (!name || INVALID_NAME.test(name)) return;

        busy = name;
        try {
            const result = await saveLocalConfig(name, overwrite);
            if (result === "exists") {
                confirmOverwrite = name;
                notify(`"${name}" already exists. Click Overwrite to replace it.`, true);
                return;
            }
            confirmOverwrite = null;
            newName = "";
            notify(overwrite ? `Overwrote "${name}" with your current settings` : `Saved "${name}"`);
            await refresh();
        } catch (e) {
            notify((e as Error).message, true);
        } finally {
            busy = null;
        }
    }

    function overwrite(name: string) {
        if (confirmOverwrite === name) {
            save(name, true);
        } else {
            confirmOverwrite = name;
        }
    }

    function formatDate(ms: number) {
        const date = new Date(ms);
        const today = new Date();
        const time = date.toLocaleTimeString([], {hour: "2-digit", minute: "2-digit"});
        if (date.toDateString() === today.toDateString()) {
            return `Today, ${time}`;
        }
        return `${date.toLocaleDateString([], {day: "numeric", month: "short", year: "numeric"})}, ${time}`;
    }

    function formatSize(bytes: number) {
        return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
    }

    onMount(refresh);
</script>

<div class="switcher">
    <form class="save-row" onsubmit={(e) => { e.preventDefault(); save(trimmedName, false); }}>
        <input
                type="text"
                placeholder="Save current settings as…"
                spellcheck="false"
                maxlength="64"
                bind:value={newName}
        />
        <button class="primary" type="submit" disabled={!trimmedName || !!nameError || busy !== null}>
            Save
        </button>
        <button class="ghost" type="button" onclick={browseLocalConfigs} title="Open the configs folder">
            <img src="img/clickgui/icon-open-file.svg" alt=""/>
            Folder
        </button>
    </form>

    {#if nameError}
        <div class="hint error">{nameError}</div>
    {:else if trimmedName && confirmOverwrite === trimmedName}
        <div class="hint">
            <button class="link" type="button" onclick={() => save(trimmedName, true)}>Overwrite "{trimmedName}"</button>
            with your current settings?
        </div>
    {/if}

    {#if !loaded}
        <div class="empty">Loading configs…</div>
    {:else if configs.length === 0}
        <div class="empty">
            No saved configs yet. Type a name above and press Save to store your current modules and settings.
        </div>
    {:else}
        <div class="list">
            {#each configs as config (config.name)}
                <div class="row" class:active={config.name === active}>
                    <div class="info">
                        <div class="name">
                            {config.name}
                            {#if config.name === active}
                                <span class="badge">Active</span>
                            {/if}
                            {#if !config.complete}
                                <span
                                        class="badge partial"
                                        title="Saved without the list of enabled modules. Loading it keeps modules it does not mention as they are. Load it, set it up, then click Overwrite to make it complete."
                                >Partial</span>
                            {/if}
                        </div>
                        <div class="meta">{formatDate(config.lastModified)} · {formatSize(config.size)}</div>
                    </div>

                    <div class="actions">
                        <button
                                class="ghost"
                                class:danger={confirmOverwrite === config.name}
                                disabled={busy !== null}
                                onclick={() => overwrite(config.name)}
                                title="Replace this config with your current settings"
                        >
                            {confirmOverwrite === config.name ? "Confirm" : "Overwrite"}
                        </button>
                        <button
                                class="primary"
                                disabled={busy !== null}
                                onclick={() => load(config.name)}
                        >
                            {busy === config.name ? "Loading…" : config.name === active ? "Reload" : "Load"}
                        </button>
                    </div>
                </div>
            {/each}
        </div>
    {/if}

    {#if message}
        <div class="toast" class:error={message.error} transition:fly={{y: 8, duration: 160}}>
            {message.text}
        </div>
    {/if}
</div>

<style lang="scss">
  .switcher {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 10px;
    max-width: 640px;
  }

  .save-row {
    display: flex;
    gap: 8px;

    input {
      all: unset;
      flex: 1;
      min-width: 0;
      padding: 9px 14px;
      border-radius: 10px;
      font-family: "Inter", sans-serif;
      font-size: var(--clickgui-font-size-sm);
      color: var(--clickgui-text-color);
      background-color: var(--clickgui-inset-color);
      border: 1px solid var(--clickgui-border-strong-color);
      transition: border-color var(--clickgui-duration-fast) var(--clickgui-ease);

      &:focus {
        border-color: var(--clickgui-focus-ring-color);
      }

      &::placeholder {
        color: var(--clickgui-text-dimmed-color);
        opacity: 0.75;
      }
    }
  }

  button {
    all: unset;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 7px 14px;
    border-radius: 9px;
    font-size: var(--clickgui-font-size-xs);
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    border-color var(--clickgui-duration-fast) var(--clickgui-ease),
    opacity var(--clickgui-duration-fast) var(--clickgui-ease);

    &:disabled {
      opacity: 0.45;
      cursor: default;
    }

    img {
      width: 12px;
      height: 12px;
      opacity: 0.8;
    }
  }

  .primary {
    color: #ffffff;
    background-color: var(--accent-color);

    &:not(:disabled):hover {
      background-color: var(--accent-hover-color);
    }
  }

  .ghost {
    color: var(--clickgui-text-dimmed-color);
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-border-strong-color);

    &:not(:disabled):hover {
      color: var(--clickgui-text-color);
    }

    &.danger {
      color: #ffffff;
      background-color: color-mix(in srgb, var(--error-color) 70%, transparent);
      border-color: var(--error-color);
    }
  }

  .link {
    padding: 0;
    font-size: inherit;
    color: color-mix(in srgb, var(--accent-color) 70%, white);
    text-decoration: underline;
  }

  .hint {
    margin-top: -4px;
    font-size: var(--clickgui-font-size-xs);
    color: var(--clickgui-text-dimmed-color);

    &.error {
      color: var(--error-color);
    }
  }

  .list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .row {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 10px 10px 10px 14px;
    border-radius: 12px;
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-card-border-color);
    transition: border-color var(--clickgui-duration-fast) var(--clickgui-ease),
    background-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      border-color: var(--clickgui-border-strong-color);
    }

    &.active {
      border-color: var(--clickgui-card-enabled-border-color);
      background-color: var(--clickgui-card-enabled-background-color);
      box-shadow: 0 0 18px color-mix(in srgb, var(--accent-color) 14%, transparent);
    }
  }

  .info {
    flex: 1;
    min-width: 0;
  }

  .name {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 700;
    color: var(--clickgui-text-color);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .badge {
    padding: 2px 7px;
    border-radius: var(--clickgui-radius-pill);
    font-size: 9px;
    font-weight: 700;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: color-mix(in srgb, var(--accent-color) 65%, white);
    background-color: color-mix(in srgb, var(--accent-color) 22%, transparent);

    &.partial {
      color: var(--warning-color);
      background-color: color-mix(in srgb, var(--warning-color) 16%, transparent);
    }
  }

  .meta {
    margin-top: 3px;
    font-size: var(--clickgui-font-size-xs);
    color: var(--clickgui-text-dimmed-color);
  }

  .actions {
    display: flex;
    gap: 6px;
    flex-shrink: 0;
  }

  .empty {
    padding: 40px 20px;
    text-align: center;
    font-size: var(--clickgui-font-size-sm);
    line-height: 1.5;
    color: var(--clickgui-text-dimmed-color);
    border-radius: 12px;
    border: 1px dashed var(--clickgui-border-strong-color);
  }

  .toast {
    position: sticky;
    bottom: 0;
    align-self: flex-start;
    padding: 8px 14px;
    border-radius: 10px;
    font-size: var(--clickgui-font-size-xs);
    font-weight: 500;
    color: var(--clickgui-text-color);
    background-color: var(--clickgui-pane-background-color);
    border: 1px solid var(--clickgui-card-enabled-border-color);
    box-shadow: var(--clickgui-elevation-2);

    &.error {
      border-color: color-mix(in srgb, var(--error-color) 60%, transparent);
    }
  }
</style>
