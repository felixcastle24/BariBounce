<script lang="ts">
    import {fly} from "svelte/transition";
    import {description, type TDescription} from "./clickgui_store";

    let data: TDescription | null = null;

    description.subscribe((v) => {
        data = v;
    });

    let element: HTMLElement | null = null;
    let left = 0;
    let anchor: "right" | "left" = "right";

    $: {
        if (data?.x !== undefined && element !== null) {
            anchor = data.anchor;
            if (data.anchor === "left") {
                left = data.x - element.clientWidth - 20;
            } else {
                left = data.x + 20;
            }
        }
    }
</script>

{#key data}
    {#if data !== null}
        <div transition:fly|global={{duration: 140, x: anchor === "right" ? -10 : 10}} class="description-wrapper"
             style="top: {data.y}px; left: {left}px;" bind:this={element}>
            <div class="description" class:right={anchor === "left"}>
                <div class="text">{data.description}</div>
            </div>
        </div>
    {/if}
{/key}

<style lang="scss">
  .description-wrapper {
    position: fixed;
    z-index: 999999999999;
    transform: translateY(-50%);
    pointer-events: none;
  }

  .description {
    position: relative;
    max-width: 280px;
    border-radius: var(--clickgui-radius-md);
    background-color: var(--clickgui-description-background-color);
    border: 1px solid var(--clickgui-border-strong-color);
    box-shadow: var(--clickgui-elevation-2);

    &::before {
      content: "";
      display: block;
      position: absolute;
      width: 8px;
      height: 8px;
      left: -5px;
      top: 50%;
      background-color: var(--clickgui-description-arrow-color);
      border-left: 1px solid var(--clickgui-border-strong-color);
      border-bottom: 1px solid var(--clickgui-border-strong-color);
      transform: translateY(-50%) rotate(45deg);
    }

    &.right {
      &::before {
        left: unset;
        right: -5px;
        transform: translateY(-50%) rotate(-135deg);
      }
    }
  }

  .text {
    font-size: var(--clickgui-font-size-sm);
    line-height: 1.45;
    padding: var(--clickgui-space-2) var(--clickgui-space-3);
    color: var(--clickgui-text-color);
  }
</style>
