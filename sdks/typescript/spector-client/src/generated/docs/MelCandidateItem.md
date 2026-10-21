
# MelCandidateItem


## Properties

Name | Type
------------ | -------------
`rank` | number
`id` | string
`tier` | string
`finalScore` | number
`scores` | [CandidateBreakdown](CandidateBreakdown.md)
`content` | string
`tags` | Array&lt;string&gt;
`importance` | number
`valence` | number
`arousal` | number
`decayFactor` | number
`createdAtMs` | number
`source` | string

## Example

```typescript
import type { MelCandidateItem } from ''

// TODO: Update the object below with actual values
const example = {
  "rank": null,
  "id": null,
  "tier": null,
  "finalScore": null,
  "scores": null,
  "content": null,
  "tags": null,
  "importance": null,
  "valence": null,
  "arousal": null,
  "decayFactor": null,
  "createdAtMs": null,
  "source": null,
} satisfies MelCandidateItem

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MelCandidateItem
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


