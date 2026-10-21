
# AnalyticsTimeseriesPoint


## Properties

Name | Type
------------ | -------------
`timestamp` | Date
`totalCount` | number
`workingCount` | number
`episodicCount` | number
`semanticCount` | number
`proceduralCount` | number
`storageBytes` | number
`recallCount` | number
`rememberCount` | number
`avgLatencyMs` | number
`consolidationsRun` | number
`dreamCyclesRun` | number
`errorCount` | number
`hebbianEdges` | number
`entityNodes` | number

## Example

```typescript
import type { AnalyticsTimeseriesPoint } from ''

// TODO: Update the object below with actual values
const example = {
  "timestamp": null,
  "totalCount": null,
  "workingCount": null,
  "episodicCount": null,
  "semanticCount": null,
  "proceduralCount": null,
  "storageBytes": null,
  "recallCount": null,
  "rememberCount": null,
  "avgLatencyMs": null,
  "consolidationsRun": null,
  "dreamCyclesRun": null,
  "errorCount": null,
  "hebbianEdges": null,
  "entityNodes": null,
} satisfies AnalyticsTimeseriesPoint

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AnalyticsTimeseriesPoint
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


