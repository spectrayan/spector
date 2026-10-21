
# ThroughputAndLatency


## Properties

Name | Type
------------ | -------------
`rememberTotal` | number
`recallTotal` | number
`rememberOpsPerSec` | number
`recallOpsPerSec` | number
`latencyP50Ms` | number
`latencyP95Ms` | number
`latencyP99Ms` | number
`latencyMeanMs` | number
`errorCount` | number
`errorRate` | number

## Example

```typescript
import type { ThroughputAndLatency } from ''

// TODO: Update the object below with actual values
const example = {
  "rememberTotal": null,
  "recallTotal": null,
  "rememberOpsPerSec": null,
  "recallOpsPerSec": null,
  "latencyP50Ms": null,
  "latencyP95Ms": null,
  "latencyP99Ms": null,
  "latencyMeanMs": null,
  "errorCount": null,
  "errorRate": null,
} satisfies ThroughputAndLatency

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as ThroughputAndLatency
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


