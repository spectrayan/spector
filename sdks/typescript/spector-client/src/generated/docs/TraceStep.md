
# TraceStep


## Properties

Name | Type
------------ | -------------
`phaseName` | string
`scoreBefore` | number
`scoreAfter` | number
`candidatesBefore` | number
`candidatesAfter` | number
`detail` | string

## Example

```typescript
import type { TraceStep } from ''

// TODO: Update the object below with actual values
const example = {
  "phaseName": null,
  "scoreBefore": null,
  "scoreAfter": null,
  "candidatesBefore": null,
  "candidatesAfter": null,
  "detail": null,
} satisfies TraceStep

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TraceStep
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


