
# RememberRequest


## Properties

Name | Type
------------ | -------------
`id` | string
`text` | string
`tier` | string
`source` | string
`tags` | string
`interest` | number
`challenge` | number
`urgency` | number
`valence` | number
`arousal` | number
`timestampMs` | number

## Example

```typescript
import type { RememberRequest } from ''

// TODO: Update the object below with actual values
const example = {
  "id": null,
  "text": null,
  "tier": null,
  "source": null,
  "tags": null,
  "interest": null,
  "challenge": null,
  "urgency": null,
  "valence": null,
  "arousal": null,
  "timestampMs": null,
} satisfies RememberRequest

console.log(example)

// Convert the instance to a JSON string
const exampleJSON: string = JSON.stringify(example)
console.log(exampleJSON)

// Parse the JSON string back to an object
const exampleParsed = JSON.parse(exampleJSON) as RememberRequest
console.log(exampleParsed)
```

[[Back to top]](#) [[Back to API list]](../README.md#api-endpoints) [[Back to Model list]](../README.md#models) [[Back to README]](../README.md)


