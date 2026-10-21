
# FunnelTelemetry


## Properties

Name | Type
------------ | -------------
`totalScanned` | number
`phase1Alive` | number
`phase2TagSurvivors` | number
`phase3ValenceSurvivors` | number
`phase4MassSurvivors` | number
`phase5SimdEvaluated` | number
`phase6FinalReturned` | number

## Example

```typescript
import type { FunnelTelemetry } from ''

// TODO: Update the object below with actual values
const example = {
  "totalScanned": null,
  "phase1Alive": null,
  "phase2TagSurvivors": null,
  "phase3ValenceSurvivors": null,
  "phase4MassSurvivors": null,
  "phase5SimdEvaluated": null,
  "phase6FinalReturned": null,
} satisfies FunnelTelemetry

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as FunnelTelemetry
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


