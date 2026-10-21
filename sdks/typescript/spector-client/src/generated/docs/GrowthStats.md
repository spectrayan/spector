
# GrowthStats


## Properties

Name | Type
------------ | -------------
`currentTotalMemories` | number
`delta24hMemories` | number
`delta7dMemories` | number
`delta30dMemories` | number
`growthRate24hPct` | number

## Example

```typescript
import type { GrowthStats } from ''

// TODO: Update the object below with actual values
const example = {
  "currentTotalMemories": null,
  "delta24hMemories": null,
  "delta7dMemories": null,
  "delta30dMemories": null,
  "growthRate24hPct": null,
} satisfies GrowthStats

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as GrowthStats
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


