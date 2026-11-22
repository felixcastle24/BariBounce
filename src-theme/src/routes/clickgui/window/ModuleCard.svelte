<script lang="ts">
    import type {Module} from "../../../integration/types";
    import {setModuleEnabled} from "../../../integration/rest";
    import {convertToSpacedString, spaceSeperatedNames} from "../../../theme/theme_config";

    let {module, selected = false, compact = false, onOpen}: {
        module: Module;
        selected?: boolean;
        compact?: boolean;
        onOpen: (name: string) => void;
    } = $props();

    const displayName = $derived($spaceSeperatedNames ? convertToSpacedString(module.name) : module.name);

    async function toggle(e?: Event) {
        e?.stopPropagation();
        await setModuleEnabled(module.name, !module.enabled);
    }

    function open(e: Event) {
        e.preventDefault();
        e.stopPropagation();
        onOpen(module.name);
    }
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
        class="card"
        class:enabled={module.enabled}
        class:selected
        class:compact
        onclick={() => toggle()}
        oncontextmenu={open}
>
    <div class="head">
        <span class="name" title={displayName}>{displayName}</span>
        <button
                class="switch"
                class:on={module.enabled}
                role="switch"
                aria-checked={module.enabled}
                aria-label="Toggle {displayName}"
                onclick={toggle}
        ></button>
    </div>

    {#if !compact}
        <p class="description">{module.description}</p>
    {/if}

    <button class="open" aria-label="Open {displayName} settings" onclick={open}>
        <span class="chevron"></span>
    </button>
</div>

<style lang="scss">
  .card {
    position: relative;
    display: flex;
    flex-direction: column;
    min-height: 92px;
    padding: 12px 12px 8px 14px;
    border-radius: 12px;
    background: linear-gradient(180deg,
            color-mix(in srgb, white 2.5%, transparent),
            transparent 60%),
    var(--clickgui-card-background-color);
    border: 1px solid var(--clickgui-card-border-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease),
    border-color var(--clickgui-duration-fast) var(--clickgui-ease),
    box-shadow var(--clickgui-duration-base) var(--clickgui-ease),
    transform var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: var(--clickgui-card-hover-background-color);
      border-color: var(--clickgui-border-strong-color);
    }

    &:active {
      transform: scale(0.985);
    }

    &.enabled {
      border-color: var(--clickgui-card-enabled-border-color);
      background-color: var(--clickgui-card-enabled-background-color);
      box-shadow: 0 0 18px color-mix(in srgb, var(--accent-color) 14%, transparent);
    }

    &.selected {
      box-shadow: 0 0 0 1px var(--accent-color) inset;
    }

    &.compact {
      min-height: 0;
      flex-direction: row;
      align-items: center;
      padding: 10px 44px 10px var(--clickgui-space-3);

      .head {
        flex: 1;
      }

      .open {
        top: 0;
        bottom: 0;
        height: auto;
      }
    }
  }

  .head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--clickgui-space-2);
  }

  .name {
    font-size: 14px;
    font-weight: 700;
    color: var(--clickgui-text-color);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .description {
    margin-top: 6px;
    margin-right: 20px;
    font-size: var(--clickgui-font-size-xs);
    line-height: 1.45;
    color: var(--clickgui-text-dimmed-color);
    opacity: 0.8;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  .switch {
    all: unset;
    position: relative;
    flex-shrink: 0;
    width: 26px;
    height: 14px;
    border-radius: var(--clickgui-radius-pill);
    background-color: var(--clickgui-switch-track-color);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-base) var(--clickgui-ease);

    &::before {
      content: "";
      position: absolute;
      top: 2px;
      left: 2px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background-color: var(--clickgui-switch-thumb-color);
      box-shadow: 0 1px 2px color-mix(in srgb, black 40%, transparent);
      transition: transform var(--clickgui-duration-base) var(--clickgui-ease);
    }

    &.on {
      background-color: var(--clickgui-switch-track-active-color);

      &::before {
        transform: translateX(12px);
        background-color: var(--clickgui-switch-thumb-active-color);
      }
    }
  }

  .open {
    all: unset;
    position: absolute;
    right: 6px;
    bottom: 6px;
    width: 28px;
    height: 24px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: var(--clickgui-radius-sm);
    cursor: pointer;
    transition: background-color var(--clickgui-duration-fast) var(--clickgui-ease);

    &:hover {
      background-color: var(--clickgui-hover-overlay-color);

      .chevron {
        opacity: 1;
      }
    }
  }

  .chevron {
    width: 6px;
    height: 6px;
    border-right: 1.5px solid var(--clickgui-text-color);
    border-bottom: 1.5px solid var(--clickgui-text-color);
    transform: translateX(-1px) rotate(-45deg);
    opacity: 0.55;
    transition: opacity var(--clickgui-duration-fast) var(--clickgui-ease);
  }
</style>
