<script lang="ts">
    import type {Module} from "../../integration/types";
    import {fade, scale} from "svelte/transition";
    import {onMount} from "svelte";
    import {getCategories, getModules} from "../../integration/rest";
    import {groupByCategory} from "../../integration/util";
    import {listen} from "../../integration/ws";
    import type {ModuleToggleEvent} from "../../integration/events";
    import {escapeLayer} from "../../integration/escape";
    import {setItem} from "../../integration/persistent_storage";
    import ScaledClickGuiContent from "./ScaledClickGuiContent.svelte";
    import GlobalSettings from "./GlobalSettings.svelte";
    import ModuleCard from "./window/ModuleCard.svelte";
    import ModuleSettingsPane from "./window/ModuleSettingsPane.svelte";
    import LiquidBounceLogo from "../../components/LiquidBounceLogo.svelte";

    const SETTINGS = "__settings";
    const ENABLED = "__enabled";
    const STATE_KEY = "clickgui.window";

    let modules = $state<Module[]>([]);
    let categories = $state<string[]>([]);
    let icons = $state<Record<string, string>>({});

    let active = $state<string>("");
    let view = $state<"grid" | "list">("grid");
    let query = $state("");
    let openModuleName = $state<string | null>(null);
    let restored = false;

    const counts = $derived(Object.fromEntries(
        categories.map(c => [c, modules.filter(m => m.category === c).length])
    ));
    const enabledTotal = $derived(modules.filter(m => m.enabled).length);

    const pureQuery = $derived(query.toLowerCase().replaceAll(" ", ""));
    const searching = $derived(pureQuery.length > 0);

    const visible = $derived.by(() => {
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
    const visibleEnabled = $derived(visible.filter(m => m.enabled).length);
    const openModule = $derived(modules.find(m => m.name === openModuleName) ?? null);

    const headingAccent = $derived(
        searching ? "Search" : active === ENABLED ? "Enabled" : active === SETTINGS ? "Client" : active
    );
    const headingRest = $derived(searching ? "Results" : active === SETTINGS ? "Settings" : "Modules");

    const layer = escapeLayer(() => {
        if (openModuleName) {
            openModuleName = null;
        } else {
            query = "";
        }
    });

    $effect(() => {
        layer.setOpen(!!openModuleName || searching);
    });

    $effect(() => {
        const state = JSON.stringify({active, view});
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
    }

    function openSettings(name: string) {
        openModuleName = openModuleName === name ? null : name;
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
            if (typeof saved.active === "string") {
                active = saved.active;
            }
        } catch {
            // Ignore a broken stored state
        }

        if (active !== SETTINGS && active !== ENABLED && !categories.includes(active)) {
            active = categories[0] ?? SETTINGS;
        }
        restored = true;
    });
</script>

<ScaledClickGuiContent>
    <div class="clickgui" transition:fade|global={{duration: 140}}>
        <div class="window" transition:scale|global={{duration: 200, start: 0.97}}>
            <nav class="sidebar">
                <div class="brand">
                    <LiquidBounceLogo width="118px" height="44px"/>
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
                        class:active={!searching && active === SETTINGS}
                        onclick={() => select(SETTINGS)}
                >
                    <img class="nav-icon" src="img/clickgui/icon-client.svg" alt=""/>
                    <span class="nav-name">Settings</span>
                </button>
            </nav>

            <main class="main">
                <header class="header">
                    <div class="heading">
                        <h1><span class="accent">{headingAccent}</span> {headingRest}</h1>
                        {#if active !== SETTINGS || searching}
                            <p>{visible.length} modules · {visibleEnabled} enabled</p>
                        {:else}
                            <p>Options that apply to the whole client</p>
                        {/if}
                    </div>

                    <div class="tools">
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
                </header>

                <div class="content">
                    {#if active === SETTINGS && !searching}
                        <div class="global-settings">
                            <GlobalSettings/>
                        </div>
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
                        <ModuleSettingsPane module={openModule} onClose={() => (openModuleName = null)}/>
                    {/key}
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
    display: grid;
    grid-template-columns: 210px 1fr;
    width: min(1060px, 92%);
    height: min(640px, 84%);
    border-radius: 18px;
    overflow: hidden;
    background-color: var(--clickgui-window-background-color);
    border: 1px solid var(--clickgui-border-strong-color);
    box-shadow: var(--clickgui-elevation-2);
    backdrop-filter: blur(var(--clickgui-blur)) saturate(140%);
  }

  /* Sidebar */

  .sidebar {
    display: flex;
    flex-direction: column;
    gap: 2px;
    padding: var(--clickgui-space-4) var(--clickgui-space-3);
    background-color: var(--clickgui-sidebar-background-color);
    border-right: 1px solid var(--clickgui-border-color);
    overflow-y: auto;

    &::-webkit-scrollbar {
      width: 0;
    }
  }

  .brand {
    padding: 2px var(--clickgui-space-2) var(--clickgui-space-4);
  }

  .section-label {
    padding: var(--clickgui-space-2) var(--clickgui-space-2) var(--clickgui-space-1);
    font-size: 10px;
    font-weight: 600;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--clickgui-text-dimmed-color);
    opacity: 0.7;
  }

  .divider {
    height: 1px;
    margin: var(--clickgui-space-3) var(--clickgui-space-2);
    background-color: var(--clickgui-border-color);
  }

  .nav-item {
    all: unset;
    display: grid;
    grid-template-columns: 16px 1fr max-content;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border-radius: var(--clickgui-radius-sm);
    font-size: var(--clickgui-font-size-md);
    font-weight: 500;
    color: var(--clickgui-text-dimmed-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: var(--clickgui-hover-overlay-color);
      color: var(--clickgui-text-color);
    }

    &.active {
      color: var(--clickgui-text-color);
      background-color: var(--clickgui-module-enabled-background-color);
      box-shadow: 0 0 0 1px var(--clickgui-card-enabled-border-color) inset;

      .nav-icon {
        opacity: 1;
      }

      .nav-count {
        color: var(--clickgui-text-color);
      }
    }
  }

  .nav-icon {
    width: 14px;
    height: 14px;
    opacity: 0.65;

    &.dot {
      width: 8px;
      height: 8px;
      margin-left: 3px;
      border-radius: 50%;
      background-color: var(--accent-color);
    }
  }

  .nav-name {
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .nav-count {
    font-size: var(--clickgui-font-size-xs);
    font-variant-numeric: tabular-nums;
    color: var(--clickgui-text-dimmed-color);
    opacity: 0.8;
  }

  /* Main area */

  .main {
    position: relative;
    display: flex;
    flex-direction: column;
    min-width: 0;
    overflow: hidden;
  }

  .header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--clickgui-space-4);
    padding: 22px 24px var(--clickgui-space-4);
  }

  .heading {
    min-width: 0;

    h1 {
      font-size: 22px;
      font-weight: 600;
      color: var(--clickgui-text-color);
      letter-spacing: -0.01em;
    }

    .accent {
      color: var(--accent-color);
    }

    p {
      margin-top: 4px;
      font-size: var(--clickgui-font-size-sm);
      color: var(--clickgui-text-dimmed-color);
    }
  }

  .tools {
    display: flex;
    align-items: center;
    gap: var(--clickgui-space-2);
    flex-shrink: 0;
  }

  .search {
    display: flex;
    align-items: center;
    gap: var(--clickgui-space-2);
    width: 240px;
    padding: 8px 12px;
    border-radius: var(--clickgui-radius-md);
    background-color: var(--clickgui-inset-color);
    border: 1px solid var(--clickgui-border-color);
    transition: border-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:focus-within {
      border-color: var(--clickgui-focus-ring-color);
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
        opacity: 0.8;
      }
    }
  }

  .view-toggle {
    display: flex;
    padding: 3px;
    gap: 2px;
    border-radius: var(--clickgui-radius-md);
    background-color: var(--clickgui-inset-color);
    border: 1px solid var(--clickgui-border-color);

    button {
      all: unset;
      width: 28px;
      height: 26px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--clickgui-radius-sm);
      cursor: pointer;
      transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease);

      svg {
        width: 14px;
        height: 14px;
        fill: none;
        stroke: var(--clickgui-text-dimmed-color);
        stroke-width: 1.6;
        stroke-linecap: round;
      }

      &:hover {
        background-color: var(--clickgui-hover-overlay-color);
      }

      &.active {
        background-color: var(--accent-color);

        svg {
          stroke: #ffffff;
        }
      }
    }
  }

  .content {
    flex: 1;
    overflow-y: auto;
    padding: 0 24px 24px;

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
    grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
    gap: 10px;

    &.list {
      grid-template-columns: 1fr;
      gap: 6px;
    }
  }

  .global-settings {
    max-width: 420px;
    padding: var(--clickgui-space-2) var(--clickgui-space-4);
    border-radius: var(--clickgui-radius-md);
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-border-color);
  }

  .empty {
    padding: 60px 0;
    text-align: center;
    font-size: var(--clickgui-font-size-md);
    color: var(--clickgui-text-dimmed-color);
  }
</style>
