<script lang="ts">
    import type {Module} from "../../integration/types";
    import {fade, scale} from "svelte/transition";
    import {onMount} from "svelte";
    import {getCategories, getClientInfo, getLocalConfigs, getModules} from "../../integration/rest";
    import {groupByCategory} from "../../integration/util";
    import {listen} from "../../integration/ws";
    import type {ModuleToggleEvent} from "../../integration/events";
    import {escapeLayer} from "../../integration/escape";
    import {setItem} from "../../integration/persistent_storage";
    import ScaledClickGuiContent from "./ScaledClickGuiContent.svelte";
    import GlobalSettings from "./GlobalSettings.svelte";
    import ModuleCard from "./window/ModuleCard.svelte";
    import ModuleSettingsPane from "./window/ModuleSettingsPane.svelte";
    import ConfigSwitcher from "./window/ConfigSwitcher.svelte";
    import MapPicker from "./window/MapPicker.svelte";
    import LiquidBounceLogo from "../../components/LiquidBounceLogo.svelte";

    type Filter = "all" | "enabled" | "disabled";

    const SETTINGS = "__settings";
    const ENABLED = "__enabled";
    const CONFIGS = "__configs";
    const STATE_KEY = "clickgui.window";
    const FILTERS: { id: Filter; label: string }[] = [
        {id: "all", label: "All"},
        {id: "enabled", label: "Enabled"},
        {id: "disabled", label: "Disabled"},
    ];

    let modules = $state<Module[]>([]);
    let categories = $state<string[]>([]);
    let icons = $state<Record<string, string>>({});
    let version = $state("");
    let configCount = $state(0);
    let activeConfig = $state("");

    let active = $state<string>("");
    let view = $state<"grid" | "list">("grid");
    let filter = $state<Filter>("all");
    let query = $state("");
    let openModuleName = $state<string | null>(null);
    let mapOpen = $state(false);
    let restored = false;

    const counts = $derived(Object.fromEntries(
        categories.map(c => [c, modules.filter(m => m.category === c).length])
    ));
    const enabledTotal = $derived(modules.filter(m => m.enabled).length);

    const pureQuery = $derived(query.toLowerCase().replaceAll(" ", ""));
    const searching = $derived(pureQuery.length > 0);
    const showingSettings = $derived(active === SETTINGS && !searching);
    const showingConfigs = $derived(active === CONFIGS && !searching);
    // Pages that show something other than module cards
    const showingPage = $derived(showingSettings || showingConfigs);

    // Modules of the current category or search, before the All/Enabled/Disabled filter
    const scope = $derived.by(() => {
        if (searching) {
            return modules.filter(m =>
                m.name.toLowerCase().includes(pureQuery)
                || m.aliases.some(a => a.toLowerCase().includes(pureQuery))
            );
        }
        if (active === ENABLED) {
            return modules.filter(m => m.enabled);
        }
        return modules.filter(m => m.category === active);
    });
    const scopeEnabled = $derived(scope.filter(m => m.enabled).length);
    const visible = $derived(
        filter === "enabled" ? scope.filter(m => m.enabled)
            : filter === "disabled" ? scope.filter(m => !m.enabled)
                : scope
    );
    const openModule = $derived(modules.find(m => m.name === openModuleName) ?? null);

    const headingAccent = $derived(
        searching ? "Search"
            : active === ENABLED ? "Enabled"
                : active === SETTINGS ? "Client"
                    : active === CONFIGS ? "Local"
                        : active
    );
    const headingRest = $derived(
        searching ? "Results" : active === SETTINGS ? "Settings" : active === CONFIGS ? "Configs" : "Modules"
    );

    const layer = escapeLayer(() => {
        if (mapOpen) {
            mapOpen = false;
        } else if (openModuleName) {
            openModuleName = null;
        } else {
            query = "";
        }
    });

    $effect(() => {
        layer.setOpen(mapOpen || !!openModuleName || searching);
    });

    $effect(() => {
        const state = JSON.stringify({active, view, filter});
        if (restored) {
            setItem(STATE_KEY, state);
        }
    });

    function iconFor(category: string) {
        return icons[category] ?? `img/clickgui/icon-${category.toLowerCase()}.svg`;
    }

    function showFallbackIcon(event: Event) {
        (event.currentTarget as HTMLImageElement).src = "img/clickgui/icon-client.svg";
    }

    function select(target: string) {
        active = target;
        query = "";
        openModuleName = null;
        mapOpen = false;
    }

    function openSettings(name: string) {
        openModuleName = openModuleName === name ? null : name;
    }

    function updateConfigInfo(count: number, activeName: string) {
        configCount = count;
        activeConfig = activeName;
    }

    async function reloadModules() {
        modules = await getModules();
    }

    listen("moduleToggle", (e: ModuleToggleEvent) => {
        const mod = modules.find(m => m.name === e.moduleName);
        if (mod) {
            mod.enabled = e.enabled;
        }
    });

    onMount(async () => {
        modules = await getModules();
        categories = Object.keys(groupByCategory(modules));
        icons = Object.fromEntries(
            (await getCategories())
                .filter(category => category.icon)
                .map(category => [category.name, category.icon!])
        );

        try {
            const saved = JSON.parse(localStorage.getItem(STATE_KEY) ?? "{}");
            if (saved.view === "grid" || saved.view === "list") {
                view = saved.view;
            }
            if (saved.filter === "all" || saved.filter === "enabled" || saved.filter === "disabled") {
                filter = saved.filter;
            }
            if (typeof saved.active === "string") {
                active = saved.active;
            }
        } catch {
            // Ignore a broken stored state
        }

        if (active !== SETTINGS && active !== ENABLED && active !== CONFIGS && !categories.includes(active)) {
            active = categories[0] ?? SETTINGS;
        }
        restored = true;

        try {
            const list = await getLocalConfigs();
            updateConfigInfo(list.configs.length, list.active);
        } catch {
            // The sidebar count is only a hint
        }

        try {
            version = (await getClientInfo()).clientVersion ?? "";
        } catch {
            version = "";
        }
    });
</script>

<ScaledClickGuiContent>
    <div class="clickgui" transition:fade|global={{duration: 140}}>
        <div class="window" transition:scale|global={{duration: 220, start: 0.96}}>
            <nav class="sidebar">
                <div class="brand">
                    <LiquidBounceLogo width="104px" height="39px"/>
                    {#if version}
                        <span class="version">Version {version}</span>
                    {/if}
                </div>

                <div class="section-label">Modules</div>
                {#each categories as category (category)}
                    <button
                            class="nav-item"
                            class:active={!searching && active === category}
                            onclick={() => select(category)}
                    >
                        <img class="nav-icon" src={iconFor(category)} alt="" onerror={showFallbackIcon}/>
                        <span class="nav-name">{category}</span>
                        <span class="nav-count">{counts[category]}</span>
                    </button>
                {/each}

                <div class="divider"></div>

                <div class="section-label">General</div>
                <button
                        class="nav-item"
                        class:active={!searching && active === ENABLED}
                        onclick={() => select(ENABLED)}
                >
                    <span class="nav-icon dot"></span>
                    <span class="nav-name">Enabled</span>
                    <span class="nav-count">{enabledTotal}</span>
                </button>
                <button
                        class="nav-item"
                        class:active={!searching && active === CONFIGS}
                        onclick={() => select(CONFIGS)}
                >
                    <img class="nav-icon" src="img/clickgui/icon-open-file.svg" alt=""/>
                    <span class="nav-name">Configs</span>
                    <span class="nav-count">{configCount}</span>
                </button>
                <button
                        class="nav-item"
                        class:active={!searching && active === SETTINGS}
                        onclick={() => select(SETTINGS)}
                >
                    <img class="nav-icon" src="img/clickgui/icon-client.svg" alt=""/>
                    <span class="nav-name">Settings</span>
                </button>
            </nav>

            <main class="main">
                <header class="header">
                    <div class="top-row">
                        <div class="heading">
                            <h1><em class="accent">{headingAccent}</em> {headingRest}</h1>
                            {#if showingSettings}
                                <p>Options that apply to the whole client</p>
                            {:else if showingConfigs}
                                <p>{configCount} saved · {activeConfig ? `${activeConfig} active` : "none loaded"}</p>
                            {:else}
                                <p>{scope.length} modules · {scopeEnabled} enabled</p>
                            {/if}
                        </div>

                        {#if active === "Baritone" && !searching}
                            <button class="map-button" onclick={() => (mapOpen = true)}>
                                <svg viewBox="0 0 16 16" aria-hidden="true">
                                    <path d="M1.5 3.5l4-1.5 5 2 4-1.5v10l-4 1.5-5-2-4 1.5z M5.5 2v10 M10.5 4v10"/>
                                </svg>
                                Map
                            </button>
                        {/if}
                        <label class="search">
                            <svg viewBox="0 0 24 24" aria-hidden="true">
                                <circle cx="11" cy="11" r="7"/>
                                <path d="M20 20l-3.5-3.5"/>
                            </svg>
                            <input
                                    type="text"
                                    placeholder="Search modules"
                                    spellcheck="false"
                                    bind:value={query}
                            />
                        </label>
                    </div>

                    {#if !showingPage}
                        <div class="bottom-row">
                            <div class="chips" role="group" aria-label="Filter modules">
                                {#each FILTERS as f (f.id)}
                                    <button
                                            class="chip"
                                            class:active={filter === f.id}
                                            onclick={() => (filter = f.id)}
                                    >
                                        {f.label}
                                    </button>
                                {/each}
                            </div>

                            <div class="view-toggle" role="group" aria-label="Layout">
                                <button
                                        class:active={view === "list"}
                                        aria-label="List view"
                                        onclick={() => (view = "list")}
                                >
                                    <svg viewBox="0 0 16 16" aria-hidden="true">
                                        <path d="M2 4h12M2 8h12M2 12h12"/>
                                    </svg>
                                </button>
                                <button
                                        class:active={view === "grid"}
                                        aria-label="Grid view"
                                        onclick={() => (view = "grid")}
                                >
                                    <svg viewBox="0 0 16 16" aria-hidden="true">
                                        <rect x="2" y="2" width="5" height="5" rx="1"/>
                                        <rect x="9" y="2" width="5" height="5" rx="1"/>
                                        <rect x="2" y="9" width="5" height="5" rx="1"/>
                                        <rect x="9" y="9" width="5" height="5" rx="1"/>
                                    </svg>
                                </button>
                            </div>
                        </div>
                    {/if}
                </header>

                <div class="content">
                    {#if showingSettings}
                        <div class="global-settings">
                            <GlobalSettings/>
                        </div>
                    {:else if showingConfigs}
                        <ConfigSwitcher onLoaded={reloadModules} onListChanged={updateConfigInfo}/>
                    {:else if visible.length === 0}
                        <div class="empty">
                            {searching ? `No modules match "${query}"` : "Nothing here yet"}
                        </div>
                    {:else}
                        <div class="cards" class:list={view === "list"}>
                            {#each visible as module (module.name)}
                                <ModuleCard
                                        {module}
                                        compact={view === "list"}
                                        selected={module.name === openModuleName}
                                        onOpen={openSettings}
                                />
                            {/each}
                        </div>
                    {/if}
                </div>

                {#if openModule}
                    {#key openModule.name}
                        <ModuleSettingsPane
                                module={openModule}
                                onClose={() => (openModuleName = null)}
                                onOpenMap={openModule.name === "Goto" ? () => { openModuleName = null; mapOpen = true; } : undefined}
                        />
                    {/key}
                {/if}

                {#if mapOpen}
                    <MapPicker onClose={() => (mapOpen = false)}/>
                {/if}
            </main>
        </div>
    </div>
</ScaledClickGuiContent>

<style lang="scss">
  .clickgui {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    padding-top: 40px;
  }

  .window {
    position: relative;
    display: grid;
    grid-template-columns: 200px 1fr;
    gap: 10px;
    padding: 10px;
    width: min(1000px, 92%);
    height: min(620px, 84%);
    border-radius: 20px;
    overflow: hidden;
    background: radial-gradient(120% 70% at 55% -10%,
            color-mix(in srgb, var(--accent-color) 16%, transparent) 0%,
            transparent 60%),
    var(--clickgui-window-background-color);
    border: 1px solid var(--clickgui-window-border-color);
    box-shadow: 0 1px 0 color-mix(in srgb, white 6%, transparent) inset,
    0 30px 80px color-mix(in srgb, black 60%, transparent);
    backdrop-filter: blur(22px) saturate(150%);
  }

  /* Sidebar */

  .sidebar {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: 14px var(--clickgui-space-2) 10px;
    border-radius: 14px;
    background-color: var(--clickgui-sidebar-background-color);
    border: 1px solid var(--clickgui-card-border-color);
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 0;
    }
  }

  .brand {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 0 10px 6px;

    .version {
      font-size: 9px;
      font-weight: 600;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--clickgui-text-dimmed-color);
      opacity: 0.75;
    }
  }

  .section-label {
    padding: 10px 10px 4px;
    font-size: 9px;
    font-weight: 600;
    letter-spacing: 0.14em;
    text-transform: uppercase;
    color: var(--clickgui-text-dimmed-color);
    opacity: 0.7;
  }

  .divider {
    height: 1px;
    margin: 8px 24% 2px;
    background-color: var(--clickgui-border-strong-color);
  }

  .nav-item {
    all: unset;
    display: grid;
    grid-template-columns: 15px 1fr max-content;
    align-items: center;
    gap: 10px;
    padding: 7px 10px;
    border-radius: 10px;
    font-size: var(--clickgui-font-size-md);
    font-weight: 500;
    color: var(--clickgui-text-dimmed-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: color-mix(in srgb, var(--accent-color) 8%, transparent);
      color: var(--clickgui-text-color);
    }

    &.active {
      color: var(--clickgui-text-color);
      background: linear-gradient(90deg,
              color-mix(in srgb, var(--accent-color) 34%, transparent),
              color-mix(in srgb, var(--accent-color) 20%, transparent));
      box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent-color) 35%, transparent) inset,
      0 4px 14px color-mix(in srgb, var(--accent-color) 18%, transparent);

      .nav-icon {
        opacity: 1;
      }

      .nav-count {
        color: var(--clickgui-text-color);
        opacity: 0.9;
      }
    }
  }

  .nav-icon {
    width: 14px;
    height: 14px;
    opacity: 0.7;

    &.dot {
      width: 7px;
      height: 7px;
      margin-left: 4px;
      border-radius: 50%;
      background-color: var(--accent-color);
      box-shadow: 0 0 8px var(--accent-color);
    }
  }

  .nav-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .nav-count {
    font-size: 10px;
    font-variant-numeric: tabular-nums;
    color: var(--clickgui-text-dimmed-color);
    opacity: 0.65;
  }

  /* Main area */

  .main {
    position: relative;
    display: flex;
    flex-direction: column;
    min-width: 0;
    overflow: hidden;
    border-radius: 14px;
  }

  .header {
    display: flex;
    flex-direction: column;
    gap: var(--clickgui-space-3);
    padding: 14px 12px var(--clickgui-space-3);
  }

  .top-row,
  .bottom-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--clickgui-space-4);
  }

  .top-row {
    align-items: flex-start;
  }

  .heading {
    min-width: 0;

    h1 {
      font-size: 24px;
      font-weight: 600;
      color: var(--clickgui-text-color);
      letter-spacing: -0.015em;
    }

    .accent {
      font-family: Georgia, "Times New Roman", serif;
      font-style: italic;
      font-weight: 500;
      color: color-mix(in srgb, var(--accent-color) 75%, white);
    }

    p {
      margin-top: 2px;
      font-size: var(--clickgui-font-size-sm);
      color: var(--clickgui-text-dimmed-color);
    }
  }

  .search {
    display: flex;
    align-items: center;
    gap: var(--clickgui-space-2);
    width: 250px;
    padding: 9px 14px;
    border-radius: 10px;
    background-color: var(--clickgui-inset-color);
    border: 1px solid var(--clickgui-border-strong-color);
    transition: border-color var(--clickgui-duration-fast) var(--clickgui-ease),
    box-shadow var(--clickgui-duration-fast) var(--clickgui-ease);

    &:focus-within {
      border-color: var(--clickgui-focus-ring-color);
      box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent-color) 15%, transparent);
    }

    svg {
      width: 14px;
      height: 14px;
      flex-shrink: 0;
      fill: none;
      stroke: var(--clickgui-text-dimmed-color);
      stroke-width: 2;
      stroke-linecap: round;
    }

    input {
      all: unset;
      flex: 1;
      min-width: 0;
      font-family: "Inter", sans-serif;
      font-size: var(--clickgui-font-size-sm);
      color: var(--clickgui-text-color);

      &::placeholder {
        color: var(--clickgui-text-dimmed-color);
        opacity: 0.75;
      }
    }
  }

  .map-button {
    all: unset;
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 8px 14px;
    border-radius: 10px;
    font-size: var(--clickgui-font-size-sm);
    font-weight: 600;
    color: #ffffff;
    background-color: var(--accent-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: var(--accent-hover-color);
    }

    svg {
      width: 14px;
      height: 14px;
      fill: none;
      stroke: currentColor;
      stroke-width: 1.4;
      stroke-linejoin: round;
    }
  }

  .chips {
    display: flex;
    gap: 6px;
  }

  .chip {
    all: unset;
    padding: 5px 12px;
    border-radius: 8px;
    font-size: var(--clickgui-font-size-xs);
    font-weight: 500;
    color: var(--clickgui-text-dimmed-color);
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-border-strong-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    color var(--clickgui-duration-fast) var(--clickgui-ease),
    border-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      color: var(--clickgui-text-color);
    }

    &.active {
      color: var(--clickgui-text-color);
      background-color: color-mix(in srgb, var(--accent-color) 22%, transparent);
      border-color: color-mix(in srgb, var(--accent-color) 50%, transparent);
    }
  }

  .view-toggle {
    display: flex;
    gap: 2px;

    button {
      all: unset;
      width: 26px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 7px;
      cursor: pointer;
      transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease);

      svg {
        width: 13px;
        height: 13px;
        fill: none;
        stroke: var(--clickgui-text-dimmed-color);
        stroke-width: 1.6;
        stroke-linecap: round;
      }

      &:hover {
        background-color: var(--clickgui-hover-overlay-color);
      }

      &.active {
        background-color: color-mix(in srgb, var(--accent-color) 25%, transparent);

        svg {
          stroke: color-mix(in srgb, var(--accent-color) 60%, white);
        }
      }
    }
  }

  .content {
    flex: 1;
    overflow-y: auto;
    padding: 0 12px 12px;

    &::-webkit-scrollbar {
      width: 4px;
    }

    &::-webkit-scrollbar-thumb {
      border-radius: var(--clickgui-radius-pill);
      background-color: var(--clickgui-border-strong-color);
    }
  }

  .cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 8px;

    &.list {
      grid-template-columns: 1fr;
      gap: 6px;
    }
  }

  .global-settings {
    max-width: 420px;
    padding: var(--clickgui-space-2) var(--clickgui-space-4);
    border-radius: 12px;
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-card-border-color);
  }

  .empty {
    padding: 60px 0;
    text-align: center;
    font-size: var(--clickgui-font-size-md);
    color: var(--clickgui-text-dimmed-color);
  }
</style>
