
# AnalyticsTimeseriesResponse


## Properties

Name | Type
------------ | -------------
`scope` | string
`tenantId` | string
`accountId` | string
`namespaceId` | string
`points` | [Array&lt;AnalyticsTimeseriesPoint&gt;](AnalyticsTimeseriesPoint.md)
`totalPoints` | number
`generatedAt` | Date

## Example

```typescript
import type { AnalyticsTimeseriesResponse } from ''

// TODO: Update the object below with actual values
const example = {
  "scope": null,
  "tenantId": null,
  "accountId": null,
  "namespaceId": null,
  "points": null,
  "totalPoints": null,
  "generatedAt": null,
} satisfies AnalyticsTimeseriesResponse

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as AnalyticsTimeseriesResponse
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


