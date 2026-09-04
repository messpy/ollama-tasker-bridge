# Local CPU inference E2E

- device: Pixel 8a / akita / 100.122.68.52:46191
- branch: develop
- commit: 6602e25465f215ef0fe8b870b74f2c52865193c5
- APK: app/build/outputs/apk/debug/app-debug.apk
- model: smollm2_135m.gguf
- model size: 105454432 bytes
- backend: static CPU

## Results

- Build: PASS (`clean`, `assembleDebug`)
- APK install: PASS
- CPU backend registration: PASS
- GGUF model load: PASS
- Context creation: PASS
- Prompt 1: PASS
- Prompt 2: PASS
- Token generation: PASS (both generations reached completion)
- Repeated local generation: PASS
- System prompt change behavior: NOT TESTED

## Evidence

The filtered Logcat is saved in `local-cpu-e2e-2026-09-04-filtered.log`.

```text
Backend registry count = 1
Backend[0] = CPU
Model path=.../smollm2_135m.gguf size=105454432 stat=0
llama_model_load_from_file result=success
context creation result=success
Sending user prompt (maxTokens=16 temperature=0.2)...
Sampler temperature=0.200000
Assistant generation complete. Awaiting user prompt...
Sending user prompt (maxTokens=16 temperature=0.2)...
Sampler temperature=0.200000
Assistant generation complete. Awaiting user prompt...
```

The previous first failure was dynamic CPU backend registration. The current static configuration registers CPU successfully. No exception, Error state, or `llama_decode` failure occurred in this run.

Next: test whether changing the system prompt with the same loaded model should reset the conversation/context, separately from CPU backend validation.
