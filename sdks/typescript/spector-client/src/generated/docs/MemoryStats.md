
# MemoryStats


## Properties

Name | Type
------------ | -------------
`totalCount` | number
`tierDistribution` | { [key: string]: number; }
`storageBytes` | number
`indexStats` | [IndexStats](IndexStats.md)
`consolidationStats` | [ConsolidationStats](ConsolidationStats.md)
`growthOverTime` | { [key: string]: number; }
`decayForecast` | { [key: string]: number; }

## Example

```typescript
import type { MemoryStats } from ''

// TODO: Update the object below with actual values
const example = {
  "totalCount": null,
  "tierDistribution": null,
  "storageBytes": null,
  "indexStats": null,
  "consolidationStats": null,
  "growthOverTime": null,
  "decayForecast": null,
} satisfies MemoryStats

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as MemoryStats
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


