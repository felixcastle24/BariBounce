<script lang="ts">
    import {onMount} from "svelte";
    import {flip} from "svelte/animate";
    import {fly} from "svelte/transition";
    import type {Module} from "../../../integration/types";
    import {getCategories, getModules} from "../../../integration/rest";
    import {listen} from "../../../integration/ws";
    import {getTextWidth} from "../../../integration/text_measurement";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";
    import type {HudModuleListSettings} from "../components";

    export let settings: { [name: string]: any };

    let cSettings = settings as HudModuleListSettings;
    let enabledModules: (Module & { label: string })[] = [];
    let icons: Record<string, string> = {};

    $: if (cSettings !== settings) {
        cSettings = settings as HudModuleListSettings;
        updateEnabledModules();
    }

    function iconFor(category: string) {
        return icons[category] ?? `img/clickgui/icon-${category.toLowerCase()}.svg`;
    }

    function showFallbackIcon(event: Event) {
        (event.currentTarget as HTMLImageElement).src = "img/clickgui/icon-client.svg";
    }

    async function updateEnabledModules() {
        const modules = (await getModules()).filter(m => m.enabled && !m.hidden);

        const withLabels = modules.map(module => ({
            ...module,
            label: $spaceSeperatedNames ? convertToSpacedString(module.name) : module.name,
        }));

        const width = (m: (typeof withLabels)[number]) => getTextWidth(
            m.tag && cSettings.showTags ? `${m.label} ${m.tag}` : m.label,
            "600 13px Inter"
        );

        switch (cSettings.sort) {
            case "Name":
                withLabels.sort((a, b) => a.label.localeCompare(b.label));
                break;
            case "Category":
                withLabels.sort((a, b) => a.category.localeCompare(b.category) || a.label.localeCompare(b.label));
                break;
            default:
                withLabels.sort((a, b) => width(b) - width(a));
        }

        enabledModules = withLabels;
    }

    spaceSeperatedNames.subscribe(() => {
        updateEnabledModules();
    });

    onMount(async () => {
        await updateEnabledModules();
        try {
            icons = Object.fromEntries(
                (await getCategories())
                    .filter(category => category.icon)
                    .map(category => [category.name, category.icon!])
            );
        } catch {
            // Falls back to the bundled icons
        }
    });

    listen("moduleToggle", updateEnabledModules);
    listen("refreshArrayList", updateEnabledModules);
</script>

{#if enabledModules.length > 0 || cSettings.showHeader}
    <div class="module-list">
        {#if cSettings.showHeader}
            <div class="header">
                <span class="title"><em>Enabled</em> Modules</span>
                <span class="count">{enabledModules.length}</span>
            </div>
        {/if}

        {#if enabledModules.length > 0}
            <div class="rows">
                {#each enabledModules as module (module.name)}
                    <div
                            class="row"
                            animate:flip={{duration: 200}}
                            transition:fly={{x: 24, duration: 160}}
                    >
                        {#if cSettings.showIcons}
                            <img class="icon" src={iconFor(module.category)} alt="" on:error={showFallbackIcon}/>
                        {/if}
                        <span class="name">{module.label}</span>
                        {#if module.tag && cSettings.showTags}
                            <span class="tag">{module.tag}</span>
                        {/if}
                    </div>
                {/each}
            </div>
        {:else}
            <div class="empty">No modules enabled</div>
        {/if}
    </div>
{/if}

<style lang="scss">
  .module-list {
    --ml-accent: var(--clickgui-window-accent-color);

    min-width: 180px;
    width: max-content;
    padding: 6px;
    border-radius: 14px;
    font-family: "Inter", sans-serif;
    background: radial-gradient(140% 90% at 50% -20%,
            color-mix(in srgb, var(--ml-accent) 18%, transparent) 0%,
            transparent 60%),
    var(--clickgui-window-background-color);
    border: 1px solid var(--clickgui-window-border-color);
    box-shadow: 0 1px 0 color-mix(in srgb, white 6%, transparent) inset,
    0 12px 30px color-mix(in srgb, black 45%, transparent);
  }

  .header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 4px 6px 8px;

    .title {
      font-size: 13px;
      font-weight: 600;
      color: var(--clickgui-text-color);

      em {
        font-family: Georgia, "Times New Roman", serif;
        font-style: italic;
        font-weight: 500;
        color: color-mix(in srgb, var(--ml-accent) 75%, white);
      }
    }

    .count {
      min-width: 20px;
      padding: 1px 7px;
      border-radius: 999px;
      text-align: center;
      font-size: 10px;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
      color: color-mix(in srgb, var(--ml-accent) 65%, white);
      background-color: color-mix(in srgb, var(--ml-accent) 22%, transparent);
    }
  }

  .rows {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }

  .row {
    position: relative;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 5px 10px 5px 12px;
    border-radius: 8px;
    background-color: var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-card-border-color);

    &::before {
      content: "";
      position: absolute;
      left: 0;
      top: 50%;
      width: 3px;
      height: 12px;
      border-radius: 999px;
      background-color: var(--ml-accent);
      box-shadow: 0 0 6px var(--ml-accent);
      transform: translateY(-50%);
    }
  }

  .icon {
    width: 12px;
    height: 12px;
    opacity: 0.75;
  }

  .name {
    font-size: 13px;
    font-weight: 600;
    color: var(--clickgui-text-color);
    white-space: nowrap;
  }

  .tag {
    margin-left: auto;
    padding-left: 6px;
    font-size: 11px;
    font-weight: 500;
    color: var(--clickgui-text-dimmed-color);
    white-space: nowrap;
  }

  .empty {
    padding: 6px 8px 4px;
    font-size: 11px;
    color: var(--clickgui-text-dimmed-color);
  }
</style>
