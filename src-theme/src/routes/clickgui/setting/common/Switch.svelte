<script lang="ts">
    import {createEventDispatcher} from "svelte";

    export let value: boolean;
    export let name: string;

    const dispatch = createEventDispatcher();
</script>

<label class="switch-container">
    <span class="switch">
        <input type="checkbox" bind:checked={value} on:change={() => dispatch("change")}/>
        <span class="slider"></span>
    </span>

    <span class="name">{name}</span>
</label>

<style lang="scss">
  .switch-container {
    display: flex;
    align-items: center;
    cursor: pointer;
  }

  .name {
    font-weight: 500;
    color: var(--clickgui-text-color);
    font-size: var(--clickgui-font-size-sm);
    margin-left: var(--clickgui-space-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .slider {
    position: absolute;
    inset: 0;
    background-color: var(--clickgui-switch-track-color);
    transition: background-color var(--clickgui-duration-base) var(--clickgui-ease);
    border-radius: var(--clickgui-radius-pill);

    &::before {
      position: absolute;
      content: "";
      height: 10px;
      width: 10px;
      top: 2px;
      left: 2px;
      background-color: var(--clickgui-switch-thumb-color);
      box-shadow: 0 1px 2px color-mix(in srgb, black 40%, transparent);
      transition: transform var(--clickgui-duration-base) var(--clickgui-ease),
      background-color var(--clickgui-duration-base) var(--clickgui-ease);
      border-radius: 50%;
    }
  }

  .switch {
    position: relative;
    flex-shrink: 0;
    width: 24px;
    height: 14px;

    input {
      display: none;
    }

    input:checked + .slider {
      background-color: var(--clickgui-switch-track-active-color);
    }

    input:checked + .slider:before {
      transform: translateX(10px);
      background-color: var(--clickgui-switch-thumb-active-color);
    }
  }
</style>
