
# CognitiveResult


## Properties

Name | Type
------------ | -------------
`id` | string
`text` | string
`score` | number
`importance` | number
`ageDays` | number
`agentRecallCount` | number
`valence` | string
`memoryType` | string
`source` | string
`synapticTags` | Array&lt;string&gt;
`decayFactor` | number
`ltpAdjustedDecay` | number
`retrievalMode` | string
`breakdown` | [ScoreBreakdown](ScoreBreakdown.md)
`trace` | [RecallTrace](RecallTrace.md)
`sourceModality` | string
`metadata` | { [key: string]: string; }
`consolidationFlags` | string
`timestampMs` | number
`truncated` | boolean
`positivelyReinforced` | boolean
`simulated` | boolean
`dreamed` | boolean
`multimodal` | boolean
`negativeOutcome` | boolean
`lateral` | boolean
`hyperfocused` | boolean

## Example

```typescript
import type { CognitiveResult } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "text": null,
  "score": null,
  "importance": null,
  "ageDays": null,
  "agentRecallCount": null,
  "valence": null,
  "memoryType": null,
  "source": null,
  "synapticTags": null,
  "decayFactor": null,
  "ltpAdjustedDecay": null,
  "retrievalMode": null,
  "breakdown": null,
  "trace": null,
  "sourceModality": null,
  "metadata": null,
  "consolidationFlags": null,
  "timestampMs": null,
  "truncated": null,
  "positivelyReinforced": null,
  "simulated": null,
  "dreamed": null,
  "multimodal": null,
  "negativeOutcome": null,
  "lateral": null,
  "hyperfocused": null,
} satisfies CognitiveResult

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as CognitiveResult
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


