# Maestro E2E Testing

## Installation

### macOS

```bash
brew tap mobile-dev-inc/tap
brew install maestro
```

### Linux

```bash
curl -Ls "https://get.maestro.mobile.dev" | bash
maestro --version
```

### Windows

Download from [Maestro releases](https://github.com/mobile-dev-inc/maestro/releases).

## Running Flows

### Single Flow

```bash
maestro test apps/android/maestro/flows/editor-smoke.yaml
```

### All Flows

```bash
maestro test apps/android/maestro/flows/*.yaml
```

### With Specific App

```bash
maestro test --appId com.onyx.android apps/android/maestro/flows/editor-smoke.yaml
```

## Available Flows

| Flow         | File                | Description                                                    |
| ------------ | ------------------- | -------------------------------------------------------------- |
| Editor Smoke | `editor-smoke.yaml` | App launch, create note, draw stroke, open PDF, verify toolbar |

## Flow Structure

```yaml
appId: com.onyx.android
name: Flow Name
tags:
  - smoke
---
- launchApp
- assertVisible: 'Element'
- tapOn: 'Button'
- takeScreenshot: final-state
```

## Common Commands

### List Connected Devices

```bash
maestro devices
```

### Upload to Maestro Cloud

```bash
maestro cloud --apiKey $MAESTRO_API_KEY \
  --app-file app/build/outputs/apk/debug/app-debug.apk \
  apps/android/maestro/flows/editor-smoke.yaml
```

### Debug Mode

```bash
maestro test --debug apps/android/maestro/flows/editor-smoke.yaml
```

## CI Integration

Maestro can run in CI via:

1. Local emulator (slow, requires setup)
2. Maestro Cloud (fast, requires API key)

### GitHub Actions Example

```yaml
- name: Run Maestro Tests
  run: |
    maestro test apps/android/maestro/flows/editor-smoke.yaml
```

## Writing New Flows

1. Create new YAML file in `apps/android/maestro/flows/`
2. Set `appId: com.onyx.android`
3. Define steps using Maestro commands
4. Add descriptive `name` and `tags`
5. Run locally to verify

### Common Assertions

- `assertVisible`: Check element is visible
- `assertNotVisible`: Check element is hidden
- `assertTrue`: Custom condition check

### Common Actions

- `tapOn`: Tap element
- `inputText`: Type text
- `swipe`: Swipe gesture
- `scrollUntilVisible`: Scroll to element
