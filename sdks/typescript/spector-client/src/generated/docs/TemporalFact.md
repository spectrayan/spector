
# TemporalFact


## Properties

Name | Type
------------ | -------------
`factId` | number
`subjectEntityId` | number
`predicateId` | number
`objectEntityId` | number
`objectTextOffset` | number
`objectTextLength` | number
`validFrom` | number
`validTo` | number
`txTime` | number
`confidence` | number
`retractsFactId` | number
`flags` | string
`ongoing` | boolean
`inferred` | boolean
`retraction` | boolean
`resolved` | boolean

## Example

```typescript
import type { TemporalFact } from ''

// TODO: Update the object below with actual values
const example = {
  "factId": null,
  "subjectEntityId": null,
  "predicateId": null,
  "objectEntityId": null,
  "objectTextOffset": null,
  "objectTextLength": null,
  "validFrom": null,
  "validTo": null,
  "txTime": null,
  "confidence": null,
  "retractsFactId": null,
  "flags": null,
  "ongoing": null,
  "inferred": null,
  "retraction": null,
  "resolved": null,
} satisfies TemporalFact

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as TemporalFact
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


