
# ScoreBreakdown


## Properties

Name | Type
------------ | -------------
`similarity` | number
`importanceDecay` | number
`tagBoostFactor` | number
`habituationPenalty` | number
`graphBoost` | number
`valenceAlignment` | number
`finalScore` | number
`epistemicWeight` | number
`teleologicalWeight` | number
`pragmaticWeight` | number
`scoringRegime` | string
`inhibitionPenalty` | number
`competitorIds` | Array&lt;string&gt;

## Example

```typescript
import type { ScoreBreakdown } from ''

// TODO: Update the object below with actual values
const example = {
  "similarity": null,
  "importanceDecay": null,
  "tagBoostFactor": null,
  "habituationPenalty": null,
  "graphBoost": null,
  "valenceAlignment": null,
  "finalScore": null,
  "epistemicWeight": null,
  "teleologicalWeight": null,
  "pragmaticWeight": null,
  "scoringRegime": null,
  "inhibitionPenalty": null,
  "competitorIds": null,
} satisfies ScoreBreakdown

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ScoreBreakdown
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


