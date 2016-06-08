var report = {"start":1455024501564,"end":1465392501564,"branches":{"master":[{"commit":"09af8240138b9dbea477d815c64f08494d329f83","time":1455152255000},{"commit":"ef578ff4d2b927929b713843c86cf03b7850fda8","time":1455151319000},{"commit":"9cc7604de1dacfe6ce26b2e11bda6e4f027a1f9d","time":1455147746000},{"commit":"6659821d8e4a62441edd95940ccf934f02f8f35b","time":1455126619000},{"commit":"0d939eaa0f272f222aad7c3dfea5da9b5e205835","time":1455092936000},{"commit":"075b33f2e6b51ab0d1b8c45333c8ddf5b9152742","time":1455076639000},{"commit":"4a576a7dad2823fcb5a76578fa51a7616a718fb8","time":1455012172000}],"2.4.x":[{"commit":"8ca96a90c1f3eff0f0a3a0de6ee5e160fff44d4a","time":1454704912000}],"2.3.x":[{"commit":"04ba3aacdf64aef229bb5f27f4136db2f2ac7eb9","time":1449969635000}]},"tests":{"scala-download-chunked-50k":"Serve an empty 50K response in 4K chunks","java-di-download-50k":"Serve an empty 50K response","java-di-template-lang":"Serve a template that takes a language argument","scala-di-json-encode":"Serve a small JSON message","scala-simple":"Serve a small plain text response","scala-di-template-lang":"Serve a template that takes a language argument","scala-di-download-chunked-50k":"Serve an empty 50K response in 4K chunks","java-di-json-encode-streaming":"Serve a small JSON message using Jackson's Streaming API","java-download-chunked-50k":"Serve an empty 50K response in 4K chunks","java-di-simple":"Serve a small plain text response","scala-template-lang":"Serve a template that takes a language argument","scala-template-simple":"Serve a template that takes a title argument","scala-download-50k":"Serve an empty 50K response","scala-di-template-simple":"Serve a template that takes a title argument","scala-di-simple":"Serve a small plain text response","scala-json-encode":"Serve a small JSON message","java-template-simple":"Serve a template that takes a title argument","java-simple":"Serve a small plain text response","java-json-encode":"Serve a small JSON message","java-template-lang":"Serve a template that takes a language argument","scala-di-download-50k":"Serve an empty 50K response","java-json-encode-streaming":"Serve a small JSON message using Jackson's Streaming API","java-di-json-encode":"Serve a small JSON message","java-di-download-chunked-50k":"Serve an empty 50K response in 4K chunks","java-di-template-simple":"Serve a template that takes a title argument","java-download-50k":"Serve an empty 50K response"},"results":{"9cc7604de1dacfe6ce26b2e11bda6e4f027a1f9d":{"java-di-download-50k":{"run":"ee8f40e9-515e-4f25-8bfc-93553d127ff3","req/s":31117.273697613877,"latMean":3.36842,"lat95":2.584},"java-di-template-lang":{"run":"072bdc4d-9880-443b-8c48-4a9169bdd062","req/s":47066.97913758829,"latMean":0.6738569999999999,"lat95":1.677},"scala-di-json-encode":{"run":"c5a5d4e0-c560-4653-9213-c7840a698a5c","req/s":52591.91775014663,"latMean":0.680948,"lat95":1.804},"scala-di-template-lang":{"run":"236d0f4f-ee08-449e-a409-aa2260454c75","req/s":51239.963057332345,"latMean":0.680704,"lat95":1.8},"scala-di-download-chunked-50k":{"run":"d23dfecd-6e22-4b9d-a455-04e48fdb3d3f","req/s":7325.603649026348,"latMean":4.43037,"lat95":9.135},"java-di-simple":{"run":"37ecd0d7-e183-4608-8c7f-fbbe28da4d4e","req/s":50547.09207569222,"latMean":0.703105,"lat95":1.833},"scala-di-template-simple":{"run":"12263ed7-583d-4af2-822a-af9a68f0230d","req/s":51379.243339180546,"latMean":0.67999,"lat95":1.811},"scala-di-simple":{"run":"d24aa98b-f252-442d-857f-3e5f200ccbba","req/s":60357.63617613051,"latMean":0.543351,"lat95":1.652},"scala-di-download-50k":{"run":"4b00a2f1-20cf-4420-ae35-7f4cadac5633","req/s":32336.003901368378,"latMean":1.04981,"lat95":2.509},"java-di-json-encode":{"run":"af8d3a32-f76f-4fde-bef0-6d88cc6babeb","req/s":46658.64535451987,"latMean":2.77987,"lat95":1.855},"java-di-download-chunked-50k":{"run":"6b773dc0-12de-4d5f-9004-861998ee700b","req/s":7023.009851364501,"latMean":4.6255500000000005,"lat95":9.461},"java-di-template-simple":{"run":"5578184b-f874-4dec-a663-0e910023b5ac","req/s":48034.116810029125,"latMean":0.799714,"lat95":1.924}},"09af8240138b9dbea477d815c64f08494d329f83":{"java-di-download-50k":{"run":"617bc9c6-956c-4b35-aa8c-95a7e4e8a3c8","req/s":31970.3142778646,"latMean":1.03043,"lat95":2.442},"java-di-template-lang":{"run":"e3ceb1da-5a2a-4cc7-bfaa-d95f1f199ef8","req/s":47527.47172615836,"latMean":2.62401,"lat95":1.748},"scala-di-json-encode":{"run":"03e8e8fc-806f-40b5-a44f-a215b85e7c77","req/s":50928.2403009574,"latMean":0.682716,"lat95":1.814},"scala-di-template-lang":{"run":"53574269-8983-4e82-adfb-3dbcfac0d6f3","req/s":52265.74957038196,"latMean":1.5577699999999999,"lat95":1.824},"scala-di-download-chunked-50k":{"run":"35b9c67b-17f4-4d02-a1d9-89a4c0b4ec05","req/s":7604.950587372041,"latMean":8.13195,"lat95":9.008},"java-di-simple":{"run":"bfe4a66f-419e-4379-9407-74c33cc4258f","req/s":49570.99559963394,"latMean":0.75881,"lat95":1.854},"scala-di-template-simple":{"run":"b01f66db-0eff-4784-8343-e65bf2222ae3","req/s":54107.24895554792,"latMean":0.6204109999999999,"lat95":1.732},"scala-di-simple":{"run":"f97206c7-4bdd-4b85-9c94-178fd925d13c","req/s":60972.22027653409,"latMean":1.4912100000000001,"lat95":1.709},"scala-di-download-50k":{"run":"b092db19-0a98-4f0c-b5ce-3388c0421458","req/s":34042.72114192745,"latMean":1.00072,"lat95":2.373},"java-di-json-encode":{"run":"e1d1b01d-b99a-423d-98ab-74a2637e9226","req/s":48786.58259702729,"latMean":0.658194,"lat95":1.722},"java-di-download-chunked-50k":{"run":"4ff63252-dd66-4a26-a9da-3f42dad36d3a","req/s":6957.358523847148,"latMean":5.69166,"lat95":9.629},"java-di-template-simple":{"run":"95ab7afd-c924-4b43-b3db-e0e886387e4f","req/s":49601.44631084513,"latMean":0.669243,"lat95":1.782}},"6659821d8e4a62441edd95940ccf934f02f8f35b":{"java-di-download-50k":{"run":"10dbb14c-3871-4c68-bdcf-2e947afca316","req/s":31504.36962609593,"latMean":1.07423,"lat95":2.471},"java-di-template-lang":{"run":"9dad5f97-7241-49ab-bc63-1f86e7718908","req/s":47284.337598144695,"latMean":0.6679189999999999,"lat95":1.702},"scala-di-json-encode":{"run":"7cbe4021-ad07-4056-b303-c0ac1e30305d","req/s":50723.94224196044,"latMean":0.853431,"lat95":1.852},"scala-di-template-lang":{"run":"1bb1b94f-a371-4bf1-9e58-2ca413598803","req/s":50450.284692603884,"latMean":0.692986,"lat95":1.823},"scala-di-download-chunked-50k":{"run":"fa0f61c5-c319-43ce-8e37-304bd0b3c224","req/s":7358.940262466781,"latMean":4.4036,"lat95":8.966},"java-di-simple":{"run":"b00db730-cb88-49ab-b569-810d8ffcfa64","req/s":50403.82485526229,"latMean":0.689567,"lat95":1.81},"scala-di-template-simple":{"run":"4e6c955f-6e53-4a68-ad32-4705a57a9961","req/s":52737.91362341537,"latMean":0.629078,"lat95":1.808},"scala-di-simple":{"run":"5b1a1f4c-6611-4ccc-835a-f61fb945507a","req/s":61921.567622184055,"latMean":0.5328189999999999,"lat95":1.654},"scala-di-download-50k":{"run":"653bf852-bf20-4a8c-9785-03cf8d28808c","req/s":33038.93457123191,"latMean":1.04216,"lat95":2.472},"java-di-json-encode":{"run":"190aacf4-8162-4c6f-9930-79481cd13063","req/s":46027.28633255005,"latMean":0.7364769999999999,"lat95":1.804},"java-di-download-chunked-50k":{"run":"75f912f1-076c-43e3-a4dd-d65279e0eb6a","req/s":6996.649106749313,"latMean":4.637569999999999,"lat95":9.473},"java-di-template-simple":{"run":"7d93c58e-d836-4682-b06f-4750b1ea0a23","req/s":48535.20751745954,"latMean":0.675331,"lat95":1.765}},"4a576a7dad2823fcb5a76578fa51a7616a718fb8":{"java-di-download-50k":{"run":"2ce7ac2e-926a-4974-bee3-f368108ec4f6","req/s":31439.171001703835,"latMean":1.39517,"lat95":2.521},"java-di-template-lang":{"run":"5d24019d-e51c-466d-854a-37b96327cb3e","req/s":47543.78047715497,"latMean":0.6658970000000001,"lat95":1.662},"scala-di-json-encode":{"run":"0f43e855-c287-4b0b-a12e-0f15ebae5fe8","req/s":50461.52742497524,"latMean":0.6889829999999999,"lat95":1.829},"scala-di-template-lang":{"run":"120814f9-87b8-4b86-af08-99b8dbb0ff34","req/s":50319.45536949111,"latMean":0.6829919999999999,"lat95":1.86},"scala-di-download-chunked-50k":{"run":"0d2916a1-7db9-4df7-b362-48a04a483641","req/s":7591.6962697472545,"latMean":4.316439999999999,"lat95":8.863},"java-di-simple":{"run":"4e96f46e-794f-4194-909f-49c27105c1ef","req/s":50166.84415891604,"latMean":0.683106,"lat95":1.856},"scala-di-template-simple":{"run":"302e6a40-be5e-4e38-bcfb-76120a4e2235","req/s":53588.29899313685,"latMean":0.639575,"lat95":1.715},"scala-di-simple":{"run":"fb9380a2-7900-478c-b00b-668786857af9","req/s":61580.607373945,"latMean":0.524788,"lat95":1.682},"scala-di-download-50k":{"run":"3f0dc0a9-aff7-435b-9323-b390ff54284a","req/s":34036.42012158719,"latMean":1.01492,"lat95":2.381},"java-di-json-encode":{"run":"3d7c4ee5-ff7d-43e1-8deb-24db99e5c64c","req/s":47031.096303535356,"latMean":0.69132,"lat95":1.785},"java-di-download-chunked-50k":{"run":"c4081320-80d5-44a1-aac6-66fa19acd31b","req/s":7110.786524238203,"latMean":4.63594,"lat95":9.413},"java-di-template-simple":{"run":"0ac05a9e-0df8-4213-ab8e-88d44a328ccf","req/s":49018.96277739073,"latMean":0.7200209999999999,"lat95":1.814}},"075b33f2e6b51ab0d1b8c45333c8ddf5b9152742":{"java-di-download-50k":{"run":"8619300f-2fd0-4311-9240-1225669e3a13","req/s":31215.248016820162,"latMean":1.17482,"lat95":2.653},"java-di-template-lang":{"run":"cca762a1-09c0-4301-8898-49547e85f740","req/s":47507.83741119837,"latMean":0.67238,"lat95":1.703},"scala-di-json-encode":{"run":"79824bca-1251-43d8-8c41-cd17179db122","req/s":53949.2598748331,"latMean":0.662854,"lat95":1.764},"scala-di-template-lang":{"run":"6e4a016c-70be-4d4e-8177-52085ac4e22b","req/s":51979.52975124092,"latMean":0.797243,"lat95":1.778},"scala-di-download-chunked-50k":{"run":"f5c214d4-361f-4ca6-a49f-add17d006e6b","req/s":7447.0378786404,"latMean":5.14532,"lat95":9.095},"java-di-simple":{"run":"a09a76bf-fc2d-4525-9488-1aead362c8d6","req/s":50571.115816259306,"latMean":0.685181,"lat95":1.847},"scala-di-template-simple":{"run":"f9665641-655e-456a-9c7c-34b1b983ea6b","req/s":51722.04156128915,"latMean":0.683942,"lat95":1.83},"scala-di-simple":{"run":"ed092b5b-94a6-4f72-95d9-60f95efcc1c6","req/s":60783.84797367353,"latMean":0.656436,"lat95":1.626},"scala-di-download-50k":{"run":"11c3cc2e-35eb-4b1d-a95a-85ccec84dcd6","req/s":32666.224416219866,"latMean":1.0446300000000002,"lat95":2.492},"java-di-json-encode":{"run":"719777f3-a8c2-4fcc-92ea-eb131ad95f1a","req/s":46871.142401800615,"latMean":0.695964,"lat95":1.743},"java-di-download-chunked-50k":{"run":"f629a174-effc-444a-8cfe-7491d572b4a4","req/s":7144.74700316959,"latMean":4.549300000000001,"lat95":9.325},"java-di-template-simple":{"run":"70bd644e-d5c4-453e-9857-56fb3d5cdc85","req/s":49566.96452400474,"latMean":1.43113,"lat95":1.798}},"ef578ff4d2b927929b713843c86cf03b7850fda8":{"java-di-download-50k":{"run":"a36a5d0e-f142-4ce5-8d7b-cb24e09b004c","req/s":31465.15066722083,"latMean":1.28153,"lat95":2.46},"java-di-template-lang":{"run":"44fb2bbe-4f37-4973-86c6-48f684c11316","req/s":47814.823967989614,"latMean":0.666727,"lat95":1.701},"scala-di-json-encode":{"run":"13307343-807b-4ac4-8dd6-5e8057ff9538","req/s":51351.34948190373,"latMean":0.696947,"lat95":1.82},"scala-di-template-lang":{"run":"4267b1ff-365a-48e3-8bc4-20bacc678e8d","req/s":51283.91719469863,"latMean":0.680557,"lat95":1.811},"scala-di-download-chunked-50k":{"run":"bdd391f3-fa2b-424d-a7ce-41a307ed49e6","req/s":7529.300351801814,"latMean":4.44256,"lat95":8.875},"java-di-simple":{"run":"59c1a712-44ac-462a-9d51-6bdce09bc1ef","req/s":50769.381907305215,"latMean":0.708332,"lat95":1.79},"scala-di-template-simple":{"run":"0883c640-454b-4f7b-a64e-9333bde54e1b","req/s":53779.323533211435,"latMean":0.627404,"lat95":1.733},"scala-di-simple":{"run":"b6853259-bd3b-4bc3-b847-d76d5d645e04","req/s":62737.843243028685,"latMean":0.612598,"lat95":1.615},"scala-di-download-50k":{"run":"add6bd37-be8e-4ed7-977f-87021eedd85f","req/s":32462.77123879004,"latMean":1.0623399999999998,"lat95":2.516},"java-di-json-encode":{"run":"a92860e9-547a-441f-9124-0696140c9813","req/s":46216.67468627584,"latMean":0.710956,"lat95":1.817},"java-di-download-chunked-50k":{"run":"43a28b11-c3fe-4c4a-8aca-deea58520892","req/s":6942.653115411905,"latMean":4.67458,"lat95":9.507},"java-di-template-simple":{"run":"e808a5b3-f2ab-4884-9d99-58c8ab5014ca","req/s":50851.49773044276,"latMean":0.647529,"lat95":1.713}},"04ba3aacdf64aef229bb5f27f4136db2f2ac7eb9":{"scala-download-chunked-50k":{"run":"d4d2fce5-ec32-463f-bc08-5a0f0af6e88c","req/s":799.5569628085511,"latMean":39.982,"lat95":39.991},"scala-simple":{"run":"f6b64687-8e0d-487f-a51a-458308bb2620","req/s":54607.116508128536,"latMean":0.580144,"lat95":1.498},"java-download-chunked-50k":{"run":"acbcf4db-4d3a-48ae-b7f2-f14069c26eb5","req/s":799.505657112503,"latMean":39.9751,"lat95":39.988},"scala-template-lang":{"run":"5b1461dc-d113-4437-b661-36e7d31cbdcc","req/s":52366.38879660444,"latMean":0.59669,"lat95":1.463},"scala-template-simple":{"run":"ba6b00c4-9107-4f14-9f6e-8a06d29c50d3","req/s":52518.7908105377,"latMean":3.44922,"lat95":1.494},"scala-download-50k":{"run":"734bb19b-ac9d-4f5e-8192-81631ad5e005","req/s":43105.40373163112,"latMean":6.175520000000001,"lat95":1.79},"scala-json-encode":{"run":"6ae9c479-1010-4b7e-bc87-acccc6907acf","req/s":53543.59438198714,"latMean":0.5979289999999999,"lat95":1.512},"java-template-simple":{"run":"64320179-8a81-41a3-8993-d5b1a1b4451e","req/s":45049.61955921171,"latMean":0.724092,"lat95":1.804},"java-simple":{"run":"37bcc0ed-f7cd-4ae4-8cdc-2f12c08bc98e","req/s":45765.99745955023,"latMean":0.6858289999999999,"lat95":1.699},"java-json-encode":{"run":"cfb64abb-b2f3-40b0-8401-bd58b04a958c","req/s":52175.881223468146,"latMean":0.618216,"lat95":1.532},"java-template-lang":{"run":"2c2d2548-3e09-4d19-9326-c663d179f817","req/s":38610.15711482188,"latMean":0.8401839999999999,"lat95":1.548},"java-download-50k":{"run":"9bf51ee0-ed0e-41d6-9ea0-9d85ac50c779","req/s":38845.76601142042,"latMean":5.44804,"lat95":1.916}},"0d939eaa0f272f222aad7c3dfea5da9b5e205835":{"java-di-download-50k":{"run":"d183028a-86bc-497d-8d89-7a7bfd252b8e","req/s":31965.86516452643,"latMean":1.03857,"lat95":2.445},"java-di-template-lang":{"run":"b05dacb9-d0bf-41b2-bdb1-3be809079bc1","req/s":46652.05668327321,"latMean":0.732839,"lat95":1.748},"scala-di-json-encode":{"run":"c821821b-c07e-4010-8b6c-85fe7a29880f","req/s":51723.45988908412,"latMean":0.683202,"lat95":1.836},"scala-di-template-lang":{"run":"646c3e51-bf6f-4371-bbd3-a10834c54c79","req/s":53304.25963823544,"latMean":4.17171,"lat95":1.788},"scala-di-download-chunked-50k":{"run":"5f59508b-c039-4384-bb0c-033621912f77","req/s":7692.076658857735,"latMean":8.20027,"lat95":8.953},"java-di-simple":{"run":"8bd790af-a39a-4ae6-b381-b7dab180420b","req/s":50998.020186053625,"latMean":0.774435,"lat95":1.785},"scala-di-template-simple":{"run":"e6580845-cd5a-44d7-8bad-534b49b27cff","req/s":53571.84631436843,"latMean":0.6259260000000001,"lat95":1.769},"scala-di-simple":{"run":"e91e6fca-95b7-41c2-b836-a5fdf702ecf7","req/s":64073.731444501136,"latMean":0.528996,"lat95":1.586},"scala-di-download-50k":{"run":"7300de71-2fa3-4d05-901c-ce7195074207","req/s":33175.79867498347,"latMean":1.01745,"lat95":2.445},"java-di-json-encode":{"run":"a266b06c-2444-4da2-ba84-d8acb1713ac8","req/s":46803.16478915099,"latMean":0.689264,"lat95":1.815},"java-di-download-chunked-50k":{"run":"a3e1e912-7daa-4bc4-849f-ebb4b6eb1c94","req/s":7015.157488343461,"latMean":4.74083,"lat95":9.591},"java-di-template-simple":{"run":"33f014f7-024b-4ac8-b984-5a5dd8495f23","req/s":48376.82739220797,"latMean":0.722793,"lat95":1.879}}}};